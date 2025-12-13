package com.example.mypdf

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin

enum class AccentLevel {
    STRONG, WEAK, MEDIUM, SILENT
}

class MetronomeEngine {
    private var job: Job? = null
    private var isRunning = false
    
    // Config
    private var bpm = 60
    private var pattern = listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK, AccentLevel.WEAK)
    
    // Audio
    private val sampleRate = 44100
    private val strongSound: ShortArray
    private val weakSound: ShortArray
    private val mediumSound: ShortArray
    
    private var audioTrack: AudioTrack? = null

    init {
        strongSound = generateTone(1200.0, 0.05) // High pitch, short
        weakSound = generateTone(800.0, 0.05)   // Lower pitch
        mediumSound = generateTone(1000.0, 0.05) // Medium pitch
        
        initAudioTrack()
    }
    
    private fun initAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        audioTrack?.play()
    }

    private fun generateTone(freq: Double, durationSec: Double): ShortArray {
        val numSamples = (durationSec * sampleRate).toInt()
        val sample = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val time = i.toDouble() / sampleRate
            // Simple sine wave with decay
            val decay = 1.0 - (i.toDouble() / numSamples)
            val value = (sin(2.0 * Math.PI * freq * time) * 32767 * decay).toInt().toShort()
            sample[i] = value
        }
        return sample
    }

    fun start(scope: CoroutineScope, bpm: Int, pattern: List<AccentLevel>, onBeat: (Int) -> Unit = {}) {
        stop()
        this.bpm = bpm
        this.pattern = pattern
        isRunning = true
        
        job = scope.launch(Dispatchers.Default) {
            var beatIndex = 0
            while (isActive && isRunning) {
                val currentBpm = this@MetronomeEngine.bpm
                val intervalMs = (60000.0 / currentBpm).toLong()
                val startTime = System.currentTimeMillis()
                
                // Play sound
                val accent = this@MetronomeEngine.pattern[beatIndex % this@MetronomeEngine.pattern.size]
                val sound = when (accent) {
                    AccentLevel.STRONG -> strongSound
                    AccentLevel.WEAK -> weakSound
                    AccentLevel.MEDIUM -> mediumSound
                    AccentLevel.SILENT -> null
                }
                
                if (sound != null) {
                    audioTrack?.write(sound, 0, sound.size)
                }
                
                // Notify UI
                withContext(Dispatchers.Main) {
                    onBeat(beatIndex % this@MetronomeEngine.pattern.size)
                }
                
                beatIndex++
                
                // Wait for next beat
                val elapsedTime = System.currentTimeMillis() - startTime
                val waitTime = intervalMs - elapsedTime
                if (waitTime > 0) {
                    delay(waitTime)
                }
            }
        }
    }

    fun updateConfig(bpm: Int, pattern: List<AccentLevel>) {
        this.bpm = bpm
        this.pattern = pattern
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }
    
    fun release() {
        stop()
        audioTrack?.stop()
        audioTrack?.release()
    }
}
