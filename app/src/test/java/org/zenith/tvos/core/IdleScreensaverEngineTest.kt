package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.screensaver.IdleScreensaverEngine

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
}
