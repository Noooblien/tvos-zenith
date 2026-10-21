package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.focus.SpatialFocusEngine
import org.zenith.tvos.core.focus.SpringPhysicsModel

class SpatialFocusEngineTest {

    @Test
    fun testFocusConstants() {
        val engine = SpatialFocusEngine()
        assertNotNull(engine)
        assertEquals(1.08f, SpatialFocusEngine.DEFAULT_FOCUS_SCALE, 0.001f)
        assertEquals(150L, SpatialFocusEngine.DEFAULT_ANIM_DURATION)
    }

    @Test
    fun testCustomFocusScaling() {
        val customEngine = SpatialFocusEngine(focusScale = 1.15f, animDurationMs = 200L, useSpringPhysics = false)
        assertNotNull(customEngine)
        assertEquals(1.15f, customEngine.focusScale, 0.001f)
        assertEquals(200L, customEngine.animDurationMs)
    }

    @Test
    fun testSpringPhysicsSelection() {
        val springEngine = SpatialFocusEngine(useSpringPhysics = true)
        assertTrue(springEngine.activeInterpolator is SpringPhysicsModel)
    }

    @Test
    fun testDirectional3dTiltAndSpecularPitchCalculations() {
        val engine = SpatialFocusEngine()

        // Initial or stationary focus -> 0f tilt
        assertEquals(0f, engine.computeDirectionalTiltY(-1, 0), 0.001f)
        assertEquals(0f, engine.computeDirectionalTiltY(2, 2), 0.001f)

        // Single step right -> +3.5 deg
        assertEquals(3.5f, engine.computeDirectionalTiltY(1, 2), 0.001f)

        // Single step left -> -3.5 deg
        assertEquals(-3.5f, engine.computeDirectionalTiltY(3, 2), 0.001f)

        // Multi-step jump clamped to 2 steps -> +7.0 deg
        assertEquals(7.0f, engine.computeDirectionalTiltY(0, 5), 0.001f)

        // Specular pitch X
        assertEquals(2.0f, engine.computeSpecularPitchX(true), 0.001f)
        assertEquals(0.0f, engine.computeSpecularPitchX(false), 0.001f)
    }

    @Test
    fun testBoundaryBounceOffsetComputation() {
        val engine = SpatialFocusEngine()
        assertEquals(-28.0f, engine.computeBoundaryBounceOffsetPx(-1, density = 2.0f), 0.001f)
        assertEquals(28.0f, engine.computeBoundaryBounceOffsetPx(1, density = 2.0f), 0.001f)
        assertEquals(0.0f, engine.computeBoundaryBounceOffsetPx(0, density = 2.0f), 0.001f)
    }
}
