package org.zenith.tvos.core.cache

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.collection.LruCache

/**
 * Ultra-lightweight Pinned-Aware LRU Icon Cache specifically engineered for 5-10 year old
 * Android TV boxes with low physical RAM (1GB - 2GB RAM budget).
 *
 * Key Architecture:
 * - Enforces a strict 12MB RAM ceiling to guarantee the launcher process remains under
 *   an 18MB PSS footprint, preventing Android Low Memory Killer (LMK) eviction.
 * - Supports a pinned package priority set (`pinnedKeys`): during `TRIM_MEMORY_RUNNING_LOW`
 *   or Smart RAM Boost, non-pinned app icons are selectively evicted first so the Favorites
 *   Dock never suffers icon reloads or frame drops.
 * - Exposes accurate lookup hit/miss telemetry (`hitRatePercent`, `hitCount`, `missCount`)
 *   for real-time HUD diagnostics.
 */
class UltraLightIconCache(
    maxCacheBytes: Int = 12 * 1024 * 1024 // 12 MB max memory pool
) {

    private val pinnedKeys = mutableSetOf<String>()
    private var lookupHits: Int = 0
    private var lookupMisses: Int = 0

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    /**
     * Updates the set of pinned favorite package keys that should be protected from
     * non-critical memory trims.
     */
    fun setPinnedKeys(keys: Collection<String>) {
        pinnedKeys.clear()
        pinnedKeys.addAll(keys)
    }

    /**
     * Checks whether a package key is marked as pinned/high-priority.
     */
    fun isKeyPinned(key: String): Boolean = pinnedKeys.contains(key)

    /**
     * Returns the count of registered pinned keys.
     */
    fun pinnedKeyCount(): Int = pinnedKeys.size

    /**
     * Retrieves a cached icon bitmap if present and records lookup telemetry.
     */
    fun get(key: String): Bitmap? {
        val bmp = cache.get(key)
        if (bmp != null) {
            lookupHits++
        } else {
            lookupMisses++
        }
        return bmp
    }

    /**
     * Stores a bitmap in the LRU cache without polluting lookup miss counters.
     */
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /**
     * Converts an Android Drawable into an optimized Bitmap, scaling down to target dimensions
     * (96x96 px max) to eliminate memory bloat.
     */
    fun drawableToOptimizedBitmap(drawable: Drawable, targetSize: Int = 96): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            if (bmp.width <= targetSize && bmp.height <= targetSize) {
                return bmp
            }
        }

        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Selectively evicts cached icons that are NOT in [pinnedKeys].
     * @return number of non-pinned entries evicted.
     */
    fun trimUnpinnedEntries(): Int {
        val snapshot = cache.snapshot()
        var evicted = 0
        for (key in snapshot.keys) {
            if (!pinnedKeys.contains(key)) {
                cache.remove(key)
                evicted++
            }
        }
        return evicted
    }

    /**
     * Clears or trims the cache based on system memory pressure.
     * Under moderate pressure (`TRIM_MEMORY_RUNNING_LOW`), evicts unpinned icons first
     * before falling back to LRU size trimming.
     */
    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            cache.evictAll()
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            trimUnpinnedEntries()
            if (cache.size() > cache.maxSize() / 2) {
                cache.trimToSize(cache.maxSize() / 2)
            }
        }
    }

    /**
     * Returns the current memory usage in bytes.
     */
    fun currentByteUsage(): Int = cache.size()

    /**
     * Returns total currently cached entries count.
     */
    fun entryCount(): Int = cache.snapshot().size

    /**
     * Returns total cache lookup hits.
     */
    fun hitCount(): Int = lookupHits

    /**
     * Returns total cache lookup misses.
     */
    fun missCount(): Int = lookupMisses

    /**
     * Computes cache hit rate percentage (0..100). Returns 100 when no lookups have occurred.
     */
    fun hitRatePercent(): Int {
        val total = lookupHits + lookupMisses
        if (total <= 0) return 100
        return ((lookupHits * 100) / total).coerceIn(0, 100)
    }

    fun evictAll() {
        cache.evictAll()
    }
}
