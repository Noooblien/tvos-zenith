package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    }
}
