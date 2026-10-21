package org.zenith.tvos.core.performance

import org.zenith.tvos.core.cache.UltraLightIconCache
import java.util.Locale

/**
 * Real-time System Health & Memory Telemetry Engine for Zenith TV.
 * Monitors JVM/ART heap footprint, UltraLightIconCache utilization & hit rate against its 12MB ceiling,
 * and FramePacer VSYNC stability. Can also execute an instant Smart RAM Boost (pinned-aware cache trim + GC).
 */
class SystemHealthEngine(
    private val framePacer: FramePacer,
    private val iconCache: UltraLightIconCache,
    private val memoryProvider: () -> Pair<Long, Long> = {
        val rt = Runtime.getRuntime()
        val used = rt.totalMemory() - rt.freeMemory()
        val max = rt.maxMemory()
        used to max
    }
) {
    data class HealthSnapshot(
        val fps: Int,
        val avgFrameTimeMs: Float,
        val droppedFrames: Long,
        val heapUsedKb: Long,
        val heapMaxKb: Long,
        val iconCacheUsedKb: Int,
        val iconCacheEntries: Int,
        val iconCacheHitRatePercent: Int = 100
    ) {
        fun formatHudLine(): String {
            val heapMb = String.format(Locale.US, "%.1f", heapUsedKb / 1024.0)
            val cacheKb = iconCacheUsedKb
            val frameMs = String.format(Locale.US, "%.1f", avgFrameTimeMs)
            return "⚡ ${fps} FPS (${frameMs}ms)  •  🧠 Heap: ${heapMb} MB  •  🖼️ IconCache: ${cacheKb} KB (${iconCacheEntries} icons, ${iconCacheHitRatePercent}% hit)  •  Jank: ${droppedFrames}"
        }
    }

    /**
     * Captures a point-in-time diagnostic snapshot.
     */
    fun captureSnapshot(): HealthSnapshot {
        val (usedBytes, maxBytes) = memoryProvider()
        val cacheBytes = try {
            iconCache.currentByteUsage()
        } catch (e: Exception) {
            0
        }
        val cacheEntries = try {
            iconCache.entryCount()
        } catch (e: Exception) {
            0
        }
        val hitRate = try {
            iconCache.hitRatePercent()
        } catch (e: Exception) {
            100
        }

        return HealthSnapshot(
            fps = framePacer.getCurrentFps(),
            avgFrameTimeMs = framePacer.getAverageFrameTimeMs(),
            droppedFrames = framePacer.totalJankFramesDropped,
            heapUsedKb = (usedBytes / 1024L).coerceAtLeast(0L),
            heapMaxKb = (maxBytes / 1024L).coerceAtLeast(1L),
            iconCacheUsedKb = (cacheBytes / 1024).coerceAtLeast(0),
            iconCacheEntries = cacheEntries.coerceAtLeast(0),
            iconCacheHitRatePercent = hitRate
        )
    }

    /**
     * Executes an immediate memory optimization pass: trims non-essential (unpinned) LruCache bitmaps
     * while preserving pinned Favorites Dock icons, and requests ART/Dalvik garbage collection.
     * @return estimated bytes reclaimed or trimmed.
     */
    fun performSmartRamBoost(): Long {
        val beforeHeap = memoryProvider().first
        try {
            iconCache.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        } catch (e: Exception) {
            // Safe fallback on unit test JVM
        }
        System.gc()
        val afterHeap = memoryProvider().first
        return (beforeHeap - afterHeap).coerceAtLeast(0L)
    }
}
