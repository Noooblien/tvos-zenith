package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Test
import org.zenith.tvos.core.performance.FramePacer

class FramePacerTest {

    @Test
    fun testTargetFrameCalculationAt60Fps() {
        val pacer = FramePacer()
        // 16.6ms is standard 60fps frame duration, should drop 0 frames
        val dropped = pacer.computeDroppedFrames(16L)
        assertEquals(0, dropped)
    }

    @Test
    fun testDroppedFramesCalculationOnJank() {
        val pacer = FramePacer()
        // At 32ms threshold or below -> 0 dropped frames
        assertEquals(0, pacer.computeDroppedFrames(32L))

        // At 50ms frame duration -> ~3 frames dropped
        val dropped50 = pacer.computeDroppedFrames(50L)
        assertEquals(3, dropped50)

        // At 100ms frame duration -> 6 frames dropped
        val dropped100 = pacer.computeDroppedFrames(100L)
        assertEquals(6, dropped100)
    }

    @Test
    fun testJankCallbackTriggered() {
        var callbackFired = false
        var recordedDropped = 0
        var recordedMs = 0L

        val pacer = FramePacer { dropped, ms ->
            callbackFired = true
            recordedDropped = dropped
            recordedMs = ms
        }

        // Test normal frame (16ms)
        pacer.evaluateFrame(16L)
        assertEquals(false, callbackFired)

        // Test jank frame (66ms -> ~3 dropped frames)
        val result = pacer.evaluateFrame(66L)
        assertEquals(true, callbackFired)
        assertEquals(3, recordedDropped)
        assertEquals(66L, recordedMs)
        assertEquals(3, result)
    }
}
