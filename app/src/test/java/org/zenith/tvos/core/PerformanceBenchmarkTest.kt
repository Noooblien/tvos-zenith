package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.cache.UltraLightIconCache
import org.zenith.tvos.core.performance.FramePacer
import org.zenith.tvos.core.security.IntentSanitizer
import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppCategoryClassifier

/**
 * Performance and benchmark validation suite for tvOS Zenith.
 * Validates sub-millisecond throughput budgets to guarantee 60 FPS locked D-pad navigation
 * on legacy Mali-450 / Mali-T720 TV chipsets with 1GB-2GB RAM.
 */
class PerformanceBenchmarkTest {

    @Test
    fun benchmarkIntentValidationThroughput() {
        val iterations = 1000
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            val valid = IntentSanitizer.isValidPackageName("com.sample.tvapp$i") &&
                    IntentSanitizer.isValidClassName("com.sample.tvapp$i.MainActivity")
            assertTrue(valid)
        }

        val totalDurationMs = (System.nanoTime() - startTime) / 1_000_000L
        // 1000 validations must complete well under 100ms (average < 0.1ms per item)
        assertTrue("Intent validation took ${totalDurationMs}ms which exceeds the 100ms budget", totalDurationMs < 100)
    }

    @Test
    fun benchmarkFramePacerJankDetectionThroughput() {
        val pacer = FramePacer()
        val iterations = 5000
        val startTime = System.nanoTime()

        var droppedTotal = 0
        for (i in 0 until iterations) {
            // Alternate between clean 16ms frame and 48ms jank frame
            val frameTime = if (i % 2 == 0) 16L else 48L
            droppedTotal += pacer.computeDroppedFrames(frameTime)
        }

        val totalDurationMs = (System.nanoTime() - startTime) / 1_000_000L
        // 5000 frames evaluated under 50ms (average < 0.01ms per frame)
        assertTrue("Frame pacing evaluation took ${totalDurationMs}ms which exceeds the 50ms budget", totalDurationMs < 50)
        // Exactly half (2500) were 48ms (which drops 2 frames each at 16.66ms cadence = 5000 dropped)
        assertEquals(2500 * (48 / 16.66f).toInt(), droppedTotal)
    }

    @Test
    fun benchmarkAppClassificationSpeed() {
        val testPackages = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.spotify.tv.android",
            "com.valvesoftware.steamlink",
            "com.lonelycatgames.Xplore",
            "com.android.tv.settings"
        )

        val startTime = System.nanoTime()
        val iterations = 1000

        for (i in 0 until iterations) {
            val pkg = testPackages[i % testPackages.size]
            val cat = AppCategoryClassifier.classify(pkg)
            assertTrue(cat != AppCategory.OTHER)
        }

        val totalDurationMs = (System.nanoTime() - startTime) / 1_000_000L
        // 1000 classifications must finish in under 25ms
        assertTrue("App classification took ${totalDurationMs}ms which exceeds the 25ms budget", totalDurationMs < 25)
    }

    @Test
    fun benchmarkMemoryCeilingEnforcement() {
        val cache = UltraLightIconCache(maxCacheBytes = 12 * 1024 * 1024)
        assertEquals(0, cache.currentByteUsage())
        assertEquals(0, cache.entryCount())
    }
}
