package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.zenith.tvos.core.focus.SpatialFocusEngine

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
        val customEngine = SpatialFocusEngine(focusScale = 1.15f, animDurationMs = 200L)
        assertNotNull(customEngine)
    }
}
