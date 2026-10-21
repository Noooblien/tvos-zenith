package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.parallax.ParallaxBackdropEngine

class ParallaxBackdropEngineTest {

    @Test
    fun testComputeNormalizedRatioEdgeCases() {
        val engine = ParallaxBackdropEngine()

        // 0 or 1 items -> 0.0
        assertEquals(0f, engine.computeNormalizedRatio(0, 0), 0.001f)
        assertEquals(0f, engine.computeNormalizedRatio(0, 1), 0.001f)

        // First item of 5 -> -1.0
        assertEquals(-1.0f, engine.computeNormalizedRatio(0, 5), 0.001f)

        // Middle item of 5 -> 0.0
        assertEquals(0.0f, engine.computeNormalizedRatio(2, 5), 0.001f)

        // Last item of 5 -> +1.0
        assertEquals(1.0f, engine.computeNormalizedRatio(4, 5), 0.001f)
    }

    @Test
    fun testComputeBackdropTranslation() {
        val engine = ParallaxBackdropEngine(maxTranslationPx = 40f)

        // Leftmost focus translates backdrop right (+40px)
        assertEquals(40f, engine.computeBackdropTranslationX(-1.0f), 0.001f)

        // Center focus translates backdrop 0px
        assertEquals(0f, engine.computeBackdropTranslationX(0.0f), 0.001f)

        // Rightmost focus translates backdrop left (-40px)
        assertEquals(-40f, engine.computeBackdropTranslationX(1.0f), 0.001f)
    }

    @Test
    fun testComputeTiltAngleY() {
        val engine = ParallaxBackdropEngine(maxTiltDegrees = 5.0f)

        assertEquals(-5.0f, engine.computeTiltAngleY(-1.0f), 0.001f)
        assertEquals(0.0f, engine.computeTiltAngleY(0.0f), 0.001f)
        assertEquals(5.0f, engine.computeTiltAngleY(1.0f), 0.001f)
    }
}
