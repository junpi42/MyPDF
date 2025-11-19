package com.example.mypdf

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

class AudioTuner {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    private val _baseFrequency = MutableStateFlow(442.0)
    val baseFrequency: StateFlow<Double> = _baseFrequency

    private val _noisyEnvironment = MutableStateFlow(false)
    val noisyEnvironment: StateFlow<Boolean> = _noisyEnvironment

    private val _tuningState = MutableStateFlow<TuningResult?>(null)
    val tuningState: StateFlow<TuningResult?> = _tuningState

    private val sampleRate = 44100
    private val frameSize = 2048 // ventana más corta → más reactivo

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(frameSize)

    // Umbrales de RMS (se aplican ANTES de cualquier normalización)
    private val baseRmsThreshold = 0.003
    private val noisyRmsThreshold = 0.010

    // Rango de pitch que queremos detectar
    private val minPitch = 70.0   // Hz
    private val maxPitch = 1200.0 // Hz

    private var hpf: Biquad? = null
    private var lpf: Biquad? = null

    // Para suavizar la frecuencia entre frames
    private var lastFrequency = 0.0

    companion object {
        private const val TAG = "AudioTuner"
    }

    fun startTuning(scope: CoroutineScope) {
        if (isRecording) return

        try {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                // Puedes probar VOICE_RECOGNITION o UNPROCESSED (si el dispositivo lo soporta)
                // para ver si mejora el comportamiento.
            } catch (e: Exception) {
                reportError(stage = "init", e = e, details = "bufferSize=$bufferSize, sampleRate=$sampleRate")
                return
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                reportError(stage = "init", details = "STATE_INITIALIZED esperado, state=${audioRecord?.state}")
                return
            }

            configureFilters()
            lastFrequency = 0.0

            try {
                audioRecord?.startRecording()
            } catch (e: Exception) {
                reportError(stage = "startRecording", e = e)
                return
            }
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(frameSize)
                while (isRecording && isActive) {
                    val read = try {
                        safeAudioRead(buffer, frameSize)
                    } catch (e: Exception) {
                        reportError(stage = "read", e = e, details = "frameSize=$frameSize")
                        break
                    }
                    if (read <= 0) continue

                    val frequency = try {
                        val rawFreq = detectFrequency(buffer, read)
                        smoothFrequency(rawFreq)
                    } catch (e: Exception) {
                        reportError(stage = "detect", e = e)
                        break
                    }

                    if (frequency <= 0.0) continue

                    val result = try {
                        analyzeFrequency(frequency)
                    } catch (e: Exception) {
                        reportError(stage = "analyze", e = e, details = "freq=$frequency")
                        break
                    }
                    _tuningState.value = result
                }
            }
        } catch (e: SecurityException) {
            reportError(stage = "permission", e = e, details = "RECORD_AUDIO no concedido", stop = false)
        } catch (e: Exception) {
            reportError(stage = "unknown", e = e)
        }
    }

    fun stopTuning(clearState: Boolean = true) {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        lastFrequency = 0.0

        if (clearState) {
            _tuningState.value = null
        }
    }

    fun setBaseFrequency(freq: Double) {
        _baseFrequency.value = freq.coerceIn(400.0, 480.0)
    }

    fun incrementBaseFrequency(step: Double = 1.0) {
        setBaseFrequency(_baseFrequency.value + step)
    }

    fun decrementBaseFrequency(step: Double = 1.0) {
        setBaseFrequency(_baseFrequency.value - step)
    }

    fun setNoisyEnvironment(enabled: Boolean) {
        _noisyEnvironment.value = enabled
        configureFilters()
    }

    private fun configureFilters() {
        if (_noisyEnvironment.value) {
            hpf = Biquad.highPass(sampleRate.toDouble(), 70.0, q = 0.707)
            lpf = Biquad.lowPass(sampleRate.toDouble(), 1500.0, q = 0.707)
        } else {
            hpf = null
            lpf = null
        }
    }

    /**
     * Suaviza la frecuencia de frame a frame para que el afinador no “salte”.
     */
    private fun smoothFrequency(raw: Double): Double {
        if (!raw.isFinite() || raw <= 0.0) {
            lastFrequency = 0.0
            return 0.0
        }
        if (lastFrequency <= 0.0) {
            lastFrequency = raw
            return raw
        }

        // Opcional: descartar cambios absurdos (ruido) muy grandes
        val ratio = raw / lastFrequency
        if (ratio < 0.5 || ratio > 2.0) {
            // Cambio > 1 octava en un frame → probablemente ruido, mantenemos valor previo
            return lastFrequency
        }

        val alpha = 0.3 // 0.1 muy suave, 0.5 más rápido
        val smoothed = alpha * raw + (1 - alpha) * lastFrequency
        lastFrequency = smoothed
        return smoothed
    }

    private fun detectFrequency(buffer: ShortArray, size: Int): Double {
        val sr = sampleRate.toDouble()
        val n = size.coerceAtMost(buffer.size)
        if (n <= 0) return 0.0

        val audio = FloatArray(n) { buffer[it].toFloat() / Short.MAX_VALUE }

        // Filtros si estamos en modo ruidoso
        if (_noisyEnvironment.value) {
            hpf?.processInPlace(audio)
            lpf?.processInPlace(audio)
        }

        // 1) Quitamos DC
        var mean = 0f
        for (i in 0 until n) {
            mean += audio[i]
        }
        mean /= n
        for (i in 0 until n) {
            audio[i] -= mean
        }

        // 2) Calculamos RMS en señal con DC eliminado (sin normalizar)
        var sumSq = 0.0
        for (i in 0 until n) {
            val v = audio[i].toDouble()
            sumSq += v * v
        }
        val rms = sqrt(sumSq / n)
        val threshold = if (_noisyEnvironment.value) noisyRmsThreshold else baseRmsThreshold
        if (rms < threshold) return 0.0

        // 3) Normalización suave: ajustar nivel, pero sin pasarnos
        val targetRms = 0.1
        val gain = (targetRms / rms).toFloat().coerceIn(0.5f, 10f)
        for (i in 0 until n) {
            audio[i] *= gain
        }

        // 4) Ventana de Hann
        for (i in 0 until n) {
            val w = 0.5f * (1f - cos(2f * Math.PI.toFloat() * i / (n - 1)))
            audio[i] *= w
        }

        // 5) Búsqueda del periodo (lag) por autocorrelación normalizada
        val minLag = (sr / maxPitch).toInt().coerceAtLeast(20)
        val maxLag = (sr / minPitch).toInt().coerceAtMost(n / 2)

        if (minLag >= maxLag) return 0.0

        var bestLag = -1
        var bestCorr = Double.NEGATIVE_INFINITY

        for (lag in minLag..maxLag) {
            val corr = normalizedCorrelationAt(audio, lag)
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }

        // Correlación demasiado baja = probablemente ruido
        if (bestLag <= 0 || !bestCorr.isFinite() || bestCorr < 0.35) {
            return 0.0
        }

        // 6) Interpolación parabólica alrededor del máximo
        val y0 = normalizedCorrelationAt(audio, bestLag - 1)
        val y1 = normalizedCorrelationAt(audio, bestLag)
        val y2 = normalizedCorrelationAt(audio, bestLag + 1)

        val denom = 2 * (y0 - 2 * y1 + y2)
        val delta = if (denom != 0.0 && y0.isFinite() && y1.isFinite() && y2.isFinite()) {
            (y0 - y2) / denom
        } else {
            0.0
        }

        val refinedLag = bestLag.toDouble() + delta.coerceIn(-1.0, 1.0)
        if (!refinedLag.isFinite() || refinedLag <= 0.0) return 0.0

        val freq = sr / refinedLag
        return if (freq.isFinite() && freq > 0.0) freq else 0.0
    }

    /**
     * Autocorrelación normalizada (NCCF).
     */
    private fun normalizedCorrelationAt(x: FloatArray, lag: Int): Double {
        if (lag <= 0 || lag >= x.size) return Double.NEGATIVE_INFINITY

        var sumNum = 0.0
        var sumX2 = 0.0
        var sumY2 = 0.0
        val end = x.size - lag

        var i = 0
        while (i < end) {
            val xi = x[i].toDouble()
            val yi = x[i + lag].toDouble()
            sumNum += xi * yi
            sumX2 += xi * xi
            sumY2 += yi * yi
            i++
        }

        val denom = sqrt(sumX2 * sumY2)
        if (denom <= 0.0) return Double.NEGATIVE_INFINITY
        return sumNum / denom
    }

    private fun analyzeFrequency(frequency: Double): TuningResult {
        if (!frequency.isFinite() || frequency < minPitch || frequency > maxPitch) {
            return TuningResult(false, frequency, "---", 0.0)
        }
        val (noteName, targetFreq) = findClosestNote(frequency)
        val centsOff = if (targetFreq.isFinite() && targetFreq > 0.0) {
            1200 * log2(frequency / targetFreq)
        } else 0.0
        val isInTune = abs(centsOff) < 10.0
        return TuningResult(isInTune, frequency, noteName, centsOff)
    }

    private fun findClosestNote(frequency: Double): Pair<String, Double> {
        val base = _baseFrequency.value
        val semitonesFromBase = 12 * log2(frequency / base)
        val closestSemitone = semitonesFromBase.roundToInt()

        val octave = 4 + Math.floorDiv(closestSemitone, 12)
        val noteIndex = Math.floorMod(9 + closestSemitone, 12)
        val noteNames = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        val noteName = noteNames[noteIndex]
        val targetFreq = base * 2.0.pow(closestSemitone / 12.0)

        return Pair("$noteName$octave", targetFreq)
    }

    private fun safeAudioRead(dst: ShortArray, size: Int): Int {
        val ar = audioRecord ?: throw IllegalStateException("AudioRecord es null")
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord no está inicializado (state=${ar.state})")
        }
        val read = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ar.read(dst, 0, size, AudioRecord.READ_BLOCKING)
        } else {
            ar.read(dst, 0, size)
        }
        if (read < 0) {
            throw IllegalStateException("READ_FAILED($read)")
        }
        if (read > dst.size) {
            throw IndexOutOfBoundsException("read=$read > dst.size=${dst.size}")
        }
        return read
    }

    private fun reportError(stage: String, e: Throwable? = null, details: String? = null, stop: Boolean = true) {
        val msg = buildString {
            append("[")
            append(stage)
            append("] ")
            if (details != null) {
                append(details)
                append(" ")
            }
            if (e != null) {
                append(e::class.simpleName)
                append(": ")
                append(e.message)
            }
        }.ifBlank { "[$stage] error" }
        Log.e(TAG, msg, e)
        _tuningState.value = TuningResult(
            isInTune = false,
            detectedFrequency = 0.0,
            targetNote = "Error",
            centsOff = 0.0,
            errorMessage = msg
        )
        if (stop) stopTuning(clearState = false)
    }
}

data class TuningResult(
    val isInTune: Boolean,
    val detectedFrequency: Double,
    val targetNote: String,
    val centsOff: Double,
    val errorMessage: String? = null
)

private class Biquad(
    private var b0: Double, private var b1: Double, private var b2: Double,
    private var a0: Double, private var a1: Double, private var a2: Double
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun processInPlace(x: FloatArray) {
        val invA0 = 1.0 / a0
        val bb0 = b0 * invA0
        val bb1 = b1 * invA0
        val bb2 = b2 * invA0
        val aa1 = a1 * invA0
        val aa2 = a2 * invA0

        var z1l = z1
        var z2l = z2

        for (i in x.indices) {
            val xn = x[i].toDouble()
            val yn = bb0 * xn + z1l
            z1l = bb1 * xn - aa1 * yn + z2l
            z2l = bb2 * xn - aa2 * yn
            x[i] = yn.toFloat()
        }

        z1 = z1l
        z2 = z2l
    }

    companion object {
        fun lowPass(fs: Double, fc: Double, q: Double = 0.707): Biquad {
            val w0 = 2.0 * Math.PI * (fc / fs)
            val alpha = sin(w0) / (2.0 * q)
            val cosw0 = cos(w0)
            val b0 = (1 - cosw0) / 2
            val b1 = 1 - cosw0
            val b2 = (1 - cosw0) / 2
            val a0 = 1 + alpha
            val a1 = -2 * cosw0
            val a2 = 1 - alpha
            return Biquad(b0, b1, b2, a0, a1, a2)
        }

        fun highPass(fs: Double, fc: Double, q: Double = 0.707): Biquad {
            val w0 = 2.0 * Math.PI * (fc / fs)
            val alpha = sin(w0) / (2.0 * q)
            val cosw0 = cos(w0)
            val b0 = (1 + cosw0) / 2
            val b1 = -(1 + cosw0)
            val b2 = (1 + cosw0) / 2
            val a0 = 1 + alpha
            val a1 = -2 * cosw0
            val a2 = 1 - alpha
            return Biquad(b0, b1, b2, a0, a1, a2)
        }
    }
}
