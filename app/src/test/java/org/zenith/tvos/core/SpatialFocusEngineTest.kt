package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.focus.SpatialFocusEngine
import org.zenith.tvos.core.focus.SpringPhysicsModel
import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppDockAdapter
import org.zenith.tvos.ui.dock.AppItem

class SpatialFocusEngineTest {

    @Test
    fun testFocusConstants() {
        val engine = SpatialFocusEngine()
        assertNotNull(engine)
        assertEquals(1.08f, SpatialFocusEngine.DEFAULT_FOCUS_SCALE, 0.001f)
        assertEquals(0.94f, SpatialFocusEngine.DEFAULT_PRESS_SCALE, 0.001f)
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
    fun testCardInnerParallaxAndOppositeSheenDisplacement() {
        val engine = SpatialFocusEngine()

        // Unfocused card resets all inner parallax planes
        val unfocused = engine.computeCardInnerParallax(hasFocus = false, tiltAngleY = 3.5f, density = 2.0f)
        assertEquals(0f, unfocused.iconTranslationX, 0.001f)
        assertEquals(0f, unfocused.iconTranslationY, 0.001f)
        assertEquals(1.0f, unfocused.iconScale, 0.001f)
        assertEquals(0f, unfocused.sheenTranslationX, 0.001f)
        assertEquals(0f, unfocused.sheenAlpha, 0.001f)

        // Focused card banking right (+3.5 deg): icon shifts right (+7.0px at 2x density), sheen shifts left (-20.0px)
        val focusedRight = engine.computeCardInnerParallax(hasFocus = true, tiltAngleY = 3.5f, density = 2.0f)
        assertEquals(7.0f, focusedRight.iconTranslationX, 0.001f)
        assertEquals(-8.0f, focusedRight.iconTranslationY, 0.001f)
        assertEquals(1.06f, focusedRight.iconScale, 0.001f)
        assertEquals(-20.0f, focusedRight.sheenTranslationX, 0.001f)
        assertEquals(0.28f, focusedRight.sheenAlpha, 0.001f)
    }

    @Test
    fun testPressScaleAndAppItemDiffCallback() {
        val engine = SpatialFocusEngine()
        assertEquals(0.94f, engine.computePressScale(isPressed = true, isFocused = true), 0.001f)
        assertEquals(1.08f, engine.computePressScale(isPressed = false, isFocused = true), 0.001f)
        assertEquals(1.0f, engine.computePressScale(isPressed = false, isFocused = false), 0.001f)

        val itemA = AppItem("com.netflix.ninja", ".MainActivity", "Netflix", null, false, AppCategory.STREAMING)
        val itemARenamed = AppItem("com.netflix.ninja", ".MainActivity", "Netflix 4K", null, false, AppCategory.STREAMING)
        val itemB = AppItem("dev.cobalt.coat", ".MainActivity", "Cobalt", null, false, AppCategory.STREAMING)

        val diff = AppDockAdapter.AppItemDiffCallback(listOf(itemA, itemB), listOf(itemARenamed, itemB))
        assertEquals(2, diff.oldListSize)
        assertEquals(2, diff.newListSize)
        assertTrue(diff.areItemsTheSame(0, 0))
        assertFalse(diff.areContentsTheSame(0, 0))
        assertTrue(diff.areContentsTheSame(1, 1))
    }

    @Test
    fun testBoundaryBounceOffsetComputation() {
        val engine = SpatialFocusEngine()
        assertEquals(-28.0f, engine.computeBoundaryBounceOffsetPx(-1, density = 2.0f), 0.001f)
        assertEquals(28.0f, engine.computeBoundaryBounceOffsetPx(1, density = 2.0f), 0.001f)
        assertEquals(0.0f, engine.computeBoundaryBounceOffsetPx(0, density = 2.0f), 0.001f)
    }
}
