package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.audio.TactileAudioEngine
import kotlin.math.abs

class TactileAudioEngineTest {

    @Test
    fun `synthesized focusTickPcm and selectPopPcm have expected sample lengths and non-zero peaks`() {
        val engine = TactileAudioEngine(isEnabled = true)

        // 14ms at 22050 Hz = 308 samples
        assertEquals(308, engine.focusTickPcm.size)
        assertTrue(engine.focusTickPcm.any { abs(it.toInt()) > 500 })

        // 28ms at 22050 Hz = 617 samples
        assertEquals(617, engine.selectPopPcm.size)
        assertTrue(engine.selectPopPcm.any { abs(it.toInt()) > 1000 })
    }

    @Test
    fun `envelope tone decays smoothly toward end of waveform`() {
        val pcm = TactileAudioEngine.generateEnvelopeTone(
            sampleRate = 22050,
            durationMs = 20,
            startFreqHz = 500.0,
            endFreqHz = 300.0,
            decayRate = 200.0,
            amplitude = 0.5
        )
        val earlyPeak = pcm.take(60).maxOf { abs(it.toInt()) }
        val latePeak = pcm.takeLast(60).maxOf { abs(it.toInt()) }
        assertTrue("Expected early envelope peak ($earlyPeak) > late peak ($latePeak)", earlyPeak > latePeak)
    }

    @Test
    fun `toggling isEnabled controls audio trigger state safely`() {
        val engine = TactileAudioEngine(isEnabled = false)
        assertFalse(engine.isEnabled)
        engine.playFocusTick()
        engine.playSelectPop()
        engine.isEnabled = true
        assertTrue(engine.isEnabled)
    }
}
