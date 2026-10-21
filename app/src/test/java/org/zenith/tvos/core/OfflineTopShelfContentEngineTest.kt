package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.recommendation.OfflineTopShelfContentEngine
import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppItem

class OfflineTopShelfContentEngineTest {

    private val engine = OfflineTopShelfContentEngine()

    @Test
    fun testStreamingContentResolution() {
        val app = AppItem("com.netflix.ninja", "com.netflix.ninja.MainActivity", "Netflix", category = AppCategory.STREAMING)
        val content = engine.resolveContent(app)

        assertEquals("Netflix", content.title)
        assertTrue(content.badgeMeta.contains("4K ULTRA HD"))
        assertEquals("▶ Watch Now", content.primaryActionLabel)
        assertEquals("●  ○  ○", content.formatCarouselDots())
    }

    @Test
    fun testGamingContentResolution() {
        val app = AppItem("com.valvesoftware.steamlink", "com.valvesoftware.steamlink.MainActivity", "Steam Link", category = AppCategory.GAMING)
        val content = engine.resolveContent(app)

        assertEquals("Steam Link", content.title)
        assertTrue(content.badgeMeta.contains("CONTROLLER READY"))
        assertEquals("▶ Play", content.primaryActionLabel)
    }

    @Test
    fun testMusicContentResolution() {
        val app = AppItem("com.spotify.tv.android", "com.spotify.tv.android.MainActivity", "Spotify", category = AppCategory.MUSIC)
        val content = engine.resolveContent(app)

        assertEquals("Spotify", content.title)
        assertTrue(content.badgeMeta.contains("LOSSLESS AUDIO"))
        assertEquals("▶ Listen", content.primaryActionLabel)
    }

    @Test
    fun testSystemContentResolution() {
        val app = AppItem("com.android.tv.settings", "com.android.tv.settings.MainSettings", "Settings", category = AppCategory.SYSTEM)
        val content = engine.resolveContent(app)

        assertEquals("Settings", content.title)
        assertTrue(content.badgeMeta.contains("SYSTEM"))
        assertEquals("Open Settings", content.primaryActionLabel)
    }

    @Test
    fun testCarouselMultiSlideResolutionAndDotFormatting() {
        val app = AppItem("com.netflix.ninja", "com.netflix.ninja.MainActivity", "Netflix", category = AppCategory.STREAMING)
        val slides = engine.resolveCarouselSlides(app)

        assertEquals(3, slides.size)
        assertEquals("●  ○  ○", slides[0].formatCarouselDots())
        assertEquals("○  ●  ○", slides[1].formatCarouselDots())
        assertEquals("○  ○  ●", slides[2].formatCarouselDots())

        // Wrapping index via resolveSlide
        val wrapped = engine.resolveSlide(app, 4) // 4 % 3 == 1
        assertEquals(slides[1].title, wrapped.title)
        assertEquals("○  ●  ○", wrapped.formatCarouselDots())
    }
}
