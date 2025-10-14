package com.example.mypdf

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

class AudioTuner {
    // Audio
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    // Estado de referencia A4 y ambiente
    private val _baseFrequency = MutableStateFlow(442.0)
    val baseFrequency: StateFlow<Double> = _baseFrequency

    private val _noisyEnvironment = MutableStateFlow(false)
    val noisyEnvironment: StateFlow<Boolean> = _noisyEnvironment

    // Estado del afinador
    private val _tuningState = MutableStateFlow<TuningResult?>(null)
    val tuningState: StateFlow<TuningResult?> = _tuningState

    // Parámetros de audio
    private val sampleRate = 44100
    private val frameSize = 4096 // ≈93 ms (mejor latencia que 8192)
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(frameSize)

    // Umbrales (se aplican según ambiente)
    private val baseMagnitudeThreshold = 0.010  // normal
    private val noisyMagnitudeThreshold = 0.030 // ambiente ruidoso (más estricto)

    // Filtros (activados sólo en ambiente ruidoso)
    private var hpf: Biquad? = null // High-pass ~70 Hz
    private var lpf: Biquad? = null // Low-pass ~1500 Hz

    fun startTuning(scope: CoroutineScope) {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _tuningState.value = TuningResult(
                    isInTune = false,
                    detectedFrequency = 0.0,
                    targetNote = "Error",
                    centsOff = 0.0,
                    errorMessage = "No se pudo inicializar el micrófono"
                )
                return
            }

            // Configura filtros si el usuario marcó ambiente ruidoso
            configureFilters()

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(frameSize)
                while (isRecording && isActive) {
                    try {
                        val read = safeAudioRead(buffer, frameSize)
                        if (read > 0) {
                            val frequency = detectFrequency(buffer, read)
                            if (frequency > 0) {
                                _tuningState.value = analyzeFrequency(frequency)
                            }
                        }
                    } catch (e: IllegalStateException) {
                        _tuningState.value = TuningResult(
                            false, 0.0, "Error", 0.0,
                            errorMessage = "AudioRecord falló: ${e.message}"
                        )
                        break
                    } catch (e: Exception) {
                        _tuningState.value = TuningResult(
                            false, 0.0, "Error", 0.0,
                            errorMessage = "Lectura de audio falló: ${e.message}"
                        )
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            _tuningState.value = TuningResult(
                isInTune = false,
                detectedFrequency = 0.0,
                targetNote = "Error",
                centsOff = 0.0,
                errorMessage = "Permiso de micrófono no concedido"
            )
        } catch (e: Exception) {
            _tuningState.value = TuningResult(
                isInTune = false,
                detectedFrequency = 0.0,
                targetNote = "Error",
                centsOff = 0.0,
                errorMessage = "Error: ${e.message}"
            )
        }
    }

    fun stopTuning() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _tuningState.value = null
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
        configureFilters() // (re)configura en caliente
    }

    private fun configureFilters() {
        if (_noisyEnvironment.value) {
            // HPF ~70 Hz, Q 0.707; LPF ~1500 Hz, Q 0.707
            hpf = Biquad.highPass(sampleRate.toDouble(), 70.0, q = 0.707)
            lpf = Biquad.lowPass(sampleRate.toDouble(), 1500.0, q = 0.707)
        } else {
            hpf = null
            lpf = null
        }
    }

    private fun detectFrequency(buffer: ShortArray, size: Int): Double {
        val sr = sampleRate.toDouble()
        val audio = FloatArray(size) { buffer[it].toFloat() / Short.MAX_VALUE }

        // Filtro pasabanda si ambiente ruidoso
        if (_noisyEnvironment.value) {
            hpf?.processInPlace(audio)
            lpf?.processInPlace(audio)
        }

        // Umbral de magnitud dependiente del ambiente
        val threshold = if (_noisyEnvironment.value) noisyMagnitudeThreshold else baseMagnitudeThreshold
        val magnitude = audio.sumOf { abs(it).toDouble() } / size
        if (magnitude < threshold) return 0.0

        // Ventana Hann
        for (i in 0 until size) {
            val w = 0.5f * (1f - cos(2f * Math.PI.toFloat() * i / (size - 1)))
            audio[i] *= w
        }

        // Limitar lags al rango 80–1200 Hz (ajústalo si quieres más grave/agudo)
        val minLag = (sr / 1200.0).toInt().coerceAtLeast(20)
        val maxLag = (sr / 80.0).toInt().coerceAtMost(size / 2)

        var bestLag = -1
        var bestCorr = Double.NEGATIVE_INFINITY

        // Autocorrelación simple
        for (lag in minLag..maxLag) {
            var corr = 0.0
            var i = 0
            val end = size - lag
            while (i < end) {
                corr += audio[i] * audio[i + lag]
                i++
            }
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }
        if (bestLag <= 0) return 0.0

        // Interpolación parabólica alrededor del pico (submuestra)
        val y0 = correlationAt(audio, bestLag - 1)
        val y1 = correlationAt(audio, bestLag)
        val y2 = correlationAt(audio, bestLag + 1)
        val denom = (2 * (y0 - 2 * y1 + y2))
        val delta = if (denom != 0.0) (y0 - y2) / denom else 0.0
        val refinedLag = bestLag.toDouble() + delta.coerceIn(-1.0, 1.0)

        if (!refinedLag.isFinite() || refinedLag <= 0.0) return 0.0
        val freq = sr / refinedLag
        return if (freq.isFinite() && freq > 0.0) freq else 0.0
    }

    private fun correlationAt(x: FloatArray, lag: Int): Double {
        if (lag <= 0 || lag >= x.size) return Double.NEGATIVE_INFINITY
        var s = 0.0
        val end = x.size - lag
        var i = 0
        while (i < end) {
            s += x[i] * x[i + lag]
            i++
        }
        return s
    }

    private fun analyzeFrequency(frequency: Double): TuningResult {
        if (!frequency.isFinite() || frequency < 80 || frequency > 1200) {
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
        val octave = 4 + (closestSemitone / 12)
        val noteIndex = ((closestSemitone % 12) + 9) % 12 // A está en índice 9
        val noteNames = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        val noteName = noteNames[noteIndex]
        val targetFreq = base * 2.0.pow(closestSemitone / 12.0)
        return Pair("$noteName$octave", targetFreq)
    }

    private fun safeAudioRead(dst: ShortArray, size: Int): Int {
        val ar = audioRecord ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ar.read(dst, 0, size, AudioRecord.READ_BLOCKING)
        } else {
            ar.read(dst, 0, size)
        }
    }
}

data class TuningResult(
    val isInTune: Boolean,
    val detectedFrequency: Double,
    val targetNote: String,
    val centsOff: Double,
    val errorMessage: String? = null
)

/** Biquad básico (RBJ) con helpers HP/LP */
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
