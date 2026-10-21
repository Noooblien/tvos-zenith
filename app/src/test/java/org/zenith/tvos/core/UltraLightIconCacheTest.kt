package org.zenith.tvos.core

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.zenith.tvos.core.cache.UltraLightIconCache

class UltraLightIconCacheTest {

    @Test
    fun testCacheInitialization() {
        val cache = UltraLightIconCache(maxCacheBytes = 8 * 1024 * 1024)
        assertNotNull(cache)
        assertEquals(0, cache.currentByteUsage())
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun testEvictAll() {
        val cache = UltraLightIconCache()
        cache.evictAll()
        assertEquals(0, cache.currentByteUsage())
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun testNonExistentKeyReturnsNull() {
        val cache = UltraLightIconCache()
        assertNull(cache.get("non.existent.package"))
    }

    @Test
    fun testOnTrimMemoryCritical() {
        val cache = UltraLightIconCache()
        cache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(0, cache.currentByteUsage())
    }

    @Test
    fun testOnTrimMemoryLow() {
        val cache = UltraLightIconCache(maxCacheBytes = 10 * 1024 * 1024)
        cache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(0, cache.currentByteUsage())
    }
}
