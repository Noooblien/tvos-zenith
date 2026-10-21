package org.zenith.tvos.core

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.cache.UltraLightIconCache

class UltraLightIconCacheTest {

    private fun mockBitmap(byteCountValue: Int = 36864): Bitmap {
        val bmp = mockk<Bitmap>(relaxed = true)
        every { bmp.byteCount } returns byteCountValue
        return bmp
    }

    @Test
    fun testCacheInitialization() {
        val cache = UltraLightIconCache(maxCacheBytes = 8 * 1024 * 1024)
        assertNotNull(cache)
        assertEquals(0, cache.currentByteUsage())
        assertEquals(0, cache.entryCount())
        assertEquals(100, cache.hitRatePercent())
    }

    @Test
    fun testEvictAll() {
        val cache = UltraLightIconCache()
        cache.put("com.netflix.ninja", mockBitmap())
        assertEquals(1, cache.entryCount())
        cache.evictAll()
        assertEquals(0, cache.currentByteUsage())
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun testHitAndMissRateTelemetry() {
        val cache = UltraLightIconCache()
        val bmp = mockBitmap()
        cache.put("com.netflix.ninja", bmp)

        // 1 miss
        assertNull(cache.get("non.existent.package"))
        assertEquals(1, cache.missCount())

        // 3 hits
        assertNotNull(cache.get("com.netflix.ninja"))
        assertNotNull(cache.get("com.netflix.ninja"))
        assertNotNull(cache.get("com.netflix.ninja"))
        assertEquals(3, cache.hitCount())

        // 3 hits / 4 total = 75%
        assertEquals(75, cache.hitRatePercent())
    }

    @Test
    fun testPinnedAwareSelectiveTrimRetainsFavorites() {
        val cache = UltraLightIconCache(maxCacheBytes = 10 * 1024 * 1024)
        cache.put("com.netflix.ninja", mockBitmap())
        cache.put("com.google.android.youtube.tv", mockBitmap())
        cache.put("com.unpinned.game", mockBitmap())

        cache.setPinnedKeys(listOf("com.netflix.ninja", "com.google.android.youtube.tv"))
        assertTrue(cache.isKeyPinned("com.netflix.ninja"))
        assertFalse(cache.isKeyPinned("com.unpinned.game"))
        assertEquals(2, cache.pinnedKeyCount())

        // TRIM_MEMORY_RUNNING_LOW evicts unpinned entries while keeping pinned dock icons
        cache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        assertNotNull(cache.get("com.netflix.ninja"))
        assertNotNull(cache.get("com.google.android.youtube.tv"))
        assertNull(cache.get("com.unpinned.game"))
        assertEquals(2, cache.entryCount())
    }

    @Test
    fun testOnTrimMemoryCriticalEvictsEverything() {
        val cache = UltraLightIconCache()
        cache.put("com.netflix.ninja", mockBitmap())
        cache.setPinnedKeys(listOf("com.netflix.ninja"))

        cache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(0, cache.currentByteUsage())
        assertEquals(0, cache.entryCount())
    }
}
