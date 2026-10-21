package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.cache.UltraLightIconCache
import org.zenith.tvos.core.performance.FramePacer
import org.zenith.tvos.core.performance.SystemHealthEngine

class SystemHealthEngineTest {

    @Test
    fun `FramePacer rolling window computes accurate FPS and jank counter`() {
        val pacer = FramePacer()
        assertEquals(60, pacer.getCurrentFps())

        // Feed 10 frames at 16ms (~60 FPS)
        repeat(10) { pacer.evaluateFrame(16L) }
        assertEquals(60, pacer.getCurrentFps())
        assertEquals(10L, pacer.totalFramesRendered)
        assertEquals(0L, pacer.totalJankFramesDropped)

        // Feed 1 janky frame of 50ms (3 dropped frames)
        val dropped = pacer.evaluateFrame(50L)
        assertEquals(3, dropped)
        assertEquals(3L, pacer.totalJankFramesDropped)
    }

    @Test
    fun `SystemHealthEngine captures formatted HUD snapshot and executes Smart RAM Boost`() {
        val pacer = FramePacer()
        repeat(5) { pacer.evaluateFrame(16L) }

        var simulatedHeapBytes = 8L * 1024L * 1024L // 8 MB
        val maxHeapBytes = 64L * 1024L * 1024L // 64 MB
        val cache = UltraLightIconCache(maxCacheBytes = 4 * 1024 * 1024)

        val engine = SystemHealthEngine(
            framePacer = pacer,
            iconCache = cache,
            memoryProvider = { simulatedHeapBytes to maxHeapBytes }
        )

        val snap = engine.captureSnapshot()
        assertEquals(60, snap.fps)
        assertEquals(8192L, snap.heapUsedKb)
        val hudText = snap.formatHudLine()
        assertTrue(hudText.contains("60 FPS"))
        assertTrue(hudText.contains("8.0 MB"))

        val reclaimed = engine.performSmartRamBoost()
        assertTrue(reclaimed >= 0L)
    }
}
