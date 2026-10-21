package org.zenith.tvos.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Zero-asset procedural PCM synthesizer for Apple TV style tactile sound feedback.
 * Generates ultra-crisp micro-clicks on D-pad focus shifts, warm harmonic pops on app launch,
 * and muted low-frequency thuds on dock boundary rubber-band bounces,
 * avoiding heavy MP3/WAV asset bloat and maintaining sub-millisecond trigger latency.
 */
class TactileAudioEngine(
    var isEnabled: Boolean = true
) {
    private val audioExecutor = Executors.newSingleThreadExecutor()

    // Pre-synthesized 16-bit PCM buffers at 22050 Hz
    val focusTickPcm: ShortArray = generateEnvelopeTone(
        sampleRate = SAMPLE_RATE,
        durationMs = 14,
        startFreqHz = 520.0,
        endFreqHz = 280.0,
        decayRate = 240.0,
        amplitude = 0.25
    )

    val selectPopPcm: ShortArray = generateEnvelopeTone(
        sampleRate = SAMPLE_RATE,
        durationMs = 28,
        startFreqHz = 440.0,
        endFreqHz = 880.0,
        decayRate = 130.0,
        amplitude = 0.35
    )

    val boundaryThudPcm: ShortArray = generateEnvelopeTone(
        sampleRate = SAMPLE_RATE,
        durationMs = 22,
        startFreqHz = 190.0,
        endFreqHz = 95.0,
        decayRate = 180.0,
        amplitude = 0.28
    )

    fun playFocusTick() {
        if (!isEnabled) return
        audioExecutor.execute {
            playPcmBuffer(focusTickPcm)
        }
    }

    fun playSelectPop() {
        if (!isEnabled) return
        audioExecutor.execute {
            playPcmBuffer(selectPopPcm)
        }
    }

    fun playBoundaryThud() {
        if (!isEnabled) return
        audioExecutor.execute {
            playPcmBuffer(boundaryThudPcm)
        }
    }

    private fun playPcmBuffer(samples: ShortArray) {
        try {
            val byteSize = samples.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize.coerceAtLeast(1024))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(35)
            track.release()
        } catch (e: Exception) {
            // Gracefully ignore on headless JVM or muted hardware
        }
    }

    companion object {
        const val SAMPLE_RATE = 22050

        /**
         * Pure mathematical synthesis of an exponentially decaying frequency-swept sine wave.
         */
        fun generateEnvelopeTone(
            sampleRate: Int,
            durationMs: Int,
            startFreqHz: Double,
            endFreqHz: Double,
            decayRate: Double,
            amplitude: Double
        ): ShortArray {
            val numSamples = (sampleRate * durationMs) / 1000
            val buffer = ShortArray(numSamples)
            var phase = 0.0

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val progress = i.toDouble() / numSamples.coerceAtLeast(1)
                val instantFreq = startFreqHz + (endFreqHz - startFreqHz) * progress
                val envelope = exp(-decayRate * t)

                val sampleValue = sin(phase) * envelope * amplitude
                phase += 2.0 * PI * instantFreq / sampleRate

                val clamped = (sampleValue * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[i] = clamped.toShort()
            }
            return buffer
        }
    }
}
