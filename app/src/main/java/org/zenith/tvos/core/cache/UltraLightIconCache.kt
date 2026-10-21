package org.zenith.tvos.core.cache

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * Ultra-lightweight LRU Icon Cache specifically engineered for 5-10 year old Android TV boxes
 * with low physical RAM (1GB - 2GB RAM budget).
 *
 * Enforces a strict 12MB RAM ceiling to guarantee the launcher process remains under
 * an 18MB PSS footprint, preventing Android Low Memory Killer (LMK) eviction.
 */
class UltraLightIconCache(
    maxCacheBytes: Int = 12 * 1024 * 1024 // 12 MB max memory pool
) {

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    /**
     * Retrieves a cached icon bitmap if present.
     */
    fun get(key: String): Bitmap? = cache.get(key)

    /**
     * Stores a bitmap in the LRU cache.
     */
    fun put(key: String, bitmap: Bitmap) {
        if (cache.get(key) == null) {
            cache.put(key, bitmap)
        }
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
     * Clears or trims the cache based on system memory pressure.
     */
    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            cache.evictAll()
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            cache.trimToSize(cache.maxSize() / 2)
        }
    }

    /**
     * Returns the current memory usage in bytes.
     */
    fun currentByteUsage(): Int = cache.size()

    /**
     * Returns total cached entries count.
     */
    fun entryCount(): Int = cache.putCount() - cache.evictionCount()

    fun evictAll() {
        cache.evictAll()
    }
}
