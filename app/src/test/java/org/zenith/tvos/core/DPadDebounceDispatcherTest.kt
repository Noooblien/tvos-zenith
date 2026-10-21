package org.zenith.tvos.core

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.input.DPadDebounceDispatcher

class DPadDebounceDispatcherTest {

    private fun mockKeyEvent(action: Int, keyCode: Int, eventTime: Long, repeatCount: Int = 0): KeyEvent {
        val event = mockk<KeyEvent>(relaxed = true)
        every { event.action } returns action
        every { event.keyCode } returns keyCode
        every { event.eventTime } returns eventTime
        every { event.repeatCount } returns repeatCount
        return event
    }

    @Test
    fun testDirectionalKeyIdentification() {
        val dispatcher = DPadDebounceDispatcher()

        assertTrue(dispatcher.isDirectionalKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(dispatcher.isDirectionalKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(dispatcher.isDirectionalKey(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(dispatcher.isDirectionalKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue(dispatcher.isDirectionalKey(KeyEvent.KEYCODE_DPAD_CENTER))

        assertFalse(dispatcher.isDirectionalKey(KeyEvent.KEYCODE_BACK))
        assertFalse(dispatcher.isDirectionalKey(KeyEvent.KEYCODE_HOME))
        assertFalse(dispatcher.isDirectionalKey(KeyEvent.KEYCODE_A))
    }

    @Test
    fun testNonDirectionalKeyAlwaysDispatches() {
        val dispatcher = DPadDebounceDispatcher()
        val backEvent = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 1000L)
        assertTrue(dispatcher.shouldDispatchKeyEvent(backEvent))
    }

    @Test
    fun testActionUpAlwaysDispatches() {
        val dispatcher = DPadDebounceDispatcher()
        val upEvent = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 1000L)
        assertTrue(dispatcher.shouldDispatchKeyEvent(upEvent))
    }

    @Test
    fun testDebouncesRapidJitterClicks() {
        val dispatcher = DPadDebounceDispatcher(debounceWindowMs = 50L)

        // First click at t=100ms -> should dispatch
        val e1 = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 100L)
        assertTrue(dispatcher.shouldDispatchKeyEvent(e1))

        // Jitter bounce click at t=120ms (delta 20ms < 50ms) -> should drop
        val e2 = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 120L)
        assertFalse(dispatcher.shouldDispatchKeyEvent(e2))

        // Next legitimate click at t=170ms (delta 70ms > 50ms) -> should dispatch
        val e3 = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 170L)
        assertTrue(dispatcher.shouldDispatchKeyEvent(e3))
    }

    @Test
    fun testHoldRepeatCadence() {
        val dispatcher = DPadDebounceDispatcher(repeatCadenceMs = 80L)

        // Initial press
        val initial = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 100L, repeatCount = 0)
        assertTrue(dispatcher.shouldDispatchKeyEvent(initial))

        // Repeat event arriving too quickly (delta 30ms < 80ms) -> drop
        val r1 = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 130L, repeatCount = 1)
        assertFalse(dispatcher.shouldDispatchKeyEvent(r1))

        // Repeat event arriving at steady 80ms cadence -> dispatch
        val r2 = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 185L, repeatCount = 2)
        assertTrue(dispatcher.shouldDispatchKeyEvent(r2))
    }

    @Test
    fun testDifferentKeysDoNotDebounceEachOther() {
        val dispatcher = DPadDebounceDispatcher(debounceWindowMs = 50L)

        // Right arrow at t=100ms
        val e1 = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 100L)
        assertTrue(dispatcher.shouldDispatchKeyEvent(e1))

        // Down arrow immediately at t=110ms (different key) -> should dispatch immediately
        val e2 = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 110L)
        assertTrue(dispatcher.shouldDispatchKeyEvent(e2))
    }
}
