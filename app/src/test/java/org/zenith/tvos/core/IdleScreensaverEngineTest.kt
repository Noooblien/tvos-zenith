package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.screensaver.IdleScreensaverEngine
import kotlin.math.abs

class IdleScreensaverEngineTest {

    @Test
    fun `transitions to active when elapsed time reaches timeoutMs`() {
        var fakeNow = 1_000L
        val engine = IdleScreensaverEngine(timeoutMs = 60_000L, timeProvider = { fakeNow })

        assertFalse(engine.isScreensaverActive)
        assertFalse(engine.checkIdleState())

        fakeNow = 60_999L
        assertFalse(engine.checkIdleState())

        fakeNow = 61_000L
        assertTrue(engine.checkIdleState())
        assertTrue(engine.isScreensaverActive)

        // Subsequent checks while already active return false (no duplicate transition)
        assertFalse(engine.checkIdleState())
    }

    @Test
    fun `recordInteraction resets timer and wakes up active screensaver`() {
        var fakeNow = 10_000L
        val engine = IdleScreensaverEngine(timeoutMs = 30_000L, timeProvider = { fakeNow })

        fakeNow = 45_000L
        assertTrue(engine.checkIdleState())
        assertTrue(engine.isScreensaverActive)

        fakeNow = 50_000L
        val wokeUp = engine.recordInteraction()
        assertTrue(wokeUp)
        assertFalse(engine.isScreensaverActive)
        assertEquals(50_000L, engine.lastInteractionTimestampMs)

        // Interaction when not active returns false
        assertFalse(engine.recordInteraction())
    }

    @Test
    fun `never activates when timeoutMs is zero or negative`() {
        var fakeNow = 0L
        val engine = IdleScreensaverEngine(timeoutMs = 0L, timeProvider = { fakeNow })

        fakeNow = 999_999_999L
        assertFalse(engine.checkIdleState())
        assertFalse(engine.isScreensaverActive)
    }

    @Test
    fun `activateNow and dismiss explicitly control screensaver state`() {
        var fakeNow = 5_000L
        val engine = IdleScreensaverEngine(timeoutMs = 120_000L, timeProvider = { fakeNow })

        engine.activateNow()
        assertTrue(engine.isScreensaverActive)

        fakeNow = 25_000L
        engine.dismiss()
        assertFalse(engine.isScreensaverActive)
        assertEquals(25_000L, engine.lastInteractionTimestampMs)
    }

    @Test
    fun `resolveVista and stepVistaIndex wrap around cleanly for D-pad scrubbing`() {
        val engine = IdleScreensaverEngine()
        assertEquals(5, engine.vistas.size)

        val first = engine.resolveVista(0)
        assertEquals("Patagonia Glaciers  •  Southern Andes, Chile", first.formatCaptionLine())
        assertTrue(first.formatTelemetryLine().contains("50.9423° S"))

        // Scrubbing left from index 0 wraps to last vista (index 4)
        val wrappedLeftIdx = engine.stepVistaIndex(0, -1)
        assertEquals(4, wrappedLeftIdx)
        assertEquals("Blue Coral Atoll", engine.resolveVista(wrappedLeftIdx).title)

        // Scrubbing right from index 4 wraps to index 0
        assertEquals(0, engine.stepVistaIndex(4, 1))
    }

    @Test
    fun `computeOledLissajousShift stays bounded and varies non-repetitively across steps`() {
        val engine = IdleScreensaverEngine()
        val s0 = engine.computeOledLissajousShift(stepIndex = 0, density = 2.0f)
        assertEquals(0f, s0.shiftXPx, 0.001f)
        assertEquals(32.0f, s0.shiftYPx, 0.001f)

        val s1 = engine.computeOledLissajousShift(stepIndex = 1, density = 2.0f)
        assertTrue(abs(s1.shiftXPx) <= 44.01f)
        assertTrue(abs(s1.shiftYPx) <= 32.01f)
        assertTrue(s1.shiftXPx > 0f)
        assertTrue(s1.shiftYPx < s0.shiftYPx)
    }
}
