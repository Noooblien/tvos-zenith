package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Test
import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppCategoryClassifier

class AppCategoryClassifierTest {

    @Test
    fun testClassifyStreamingApps() {
        assertEquals(AppCategory.STREAMING, AppCategoryClassifier.classify("com.netflix.ninja", "Netflix"))
        assertEquals(AppCategory.STREAMING, AppCategoryClassifier.classify("com.google.android.youtube.tv", "YouTube"))
        assertEquals(AppCategory.STREAMING, AppCategoryClassifier.classify("com.plexapp.android", "Plex"))
        assertEquals(AppCategory.STREAMING, AppCategoryClassifier.classify("com.amazon.amazonvideo.livingroom", "Prime Video"))
        assertEquals(AppCategory.STREAMING, AppCategoryClassifier.classify("org.videolan.vlc", "VLC"))
    }

    @Test
    fun testClassifyMusicApps() {
        assertEquals(AppCategory.MUSIC, AppCategoryClassifier.classify("com.spotify.tv.android", "Spotify"))
        assertEquals(AppCategory.MUSIC, AppCategoryClassifier.classify("deezer.android.tv", "Deezer"))
        assertEquals(AppCategory.MUSIC, AppCategoryClassifier.classify("com.apple.android.music", "Apple Music"))
    }

    @Test
    fun testClassifyGamingApps() {
        assertEquals(AppCategory.GAMING, AppCategoryClassifier.classify("com.valvesoftware.steamlink", "Steam Link"))
        assertEquals(AppCategory.GAMING, AppCategoryClassifier.classify("com.retroarch", "RetroArch"))
        assertEquals(AppCategory.GAMING, AppCategoryClassifier.classify("com.nvidia.geforcenow", "GeForce NOW"))
    }

    @Test
    fun testClassifySystemAndTools() {
        assertEquals(AppCategory.SYSTEM, AppCategoryClassifier.classify("com.android.tv.settings", "Settings", isSystemApp = true))
        assertEquals(AppCategory.TOOLS, AppCategoryClassifier.classify("com.lonelycatgames.Xplore", "X-plore File Manager"))
    }
}
