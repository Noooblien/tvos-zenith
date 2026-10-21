package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.zenith.tvos.core.library.AppLibraryManager
import org.zenith.tvos.core.library.AppPreferencesStorage
import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppItem

class AppLibraryManagerTest {

    private class MockStorage : AppPreferencesStorage {
        var savedFavorites: List<String>? = null
        var savedHidden: Set<String>? = null

        override fun saveFavorites(packageNames: List<String>) {
            savedFavorites = ArrayList(packageNames)
        }

        override fun loadFavorites(): List<String>? = savedFavorites

        override fun saveHidden(packageNames: Set<String>) {
            savedHidden = HashSet(packageNames)
        }

        override fun loadHidden(): Set<String>? = savedHidden
    }

    private lateinit var storage: MockStorage
    private lateinit var manager: AppLibraryManager

    private val sampleApps = listOf(
        AppItem("com.netflix.ninja", "com.netflix.ninja.MainActivity", "Netflix", category = AppCategory.STREAMING),
        AppItem("com.google.android.youtube.tv", "com.google.android.youtube.tv.MainActivity", "YouTube", category = AppCategory.STREAMING),
        AppItem("com.spotify.tv.android", "com.spotify.tv.android.MainActivity", "Spotify", category = AppCategory.MUSIC),
        AppItem("com.valvesoftware.steamlink", "com.valvesoftware.steamlink.MainActivity", "Steam Link", category = AppCategory.GAMING),
        AppItem("com.android.tv.settings", "com.android.tv.settings.MainSettings", "Settings", isSystemApp = true, category = AppCategory.SYSTEM),
        AppItem("com.lonelycatgames.Xplore", "com.lonelycatgames.Xplore.MainActivity", "Files", category = AppCategory.TOOLS),
        AppItem("org.videolan.vlc", "org.videolan.vlc.MainActivity", "VLC", category = AppCategory.STREAMING)
    )

    @Before
    fun setup() {
        storage = MockStorage()
        manager = AppLibraryManager(storage)
    }

    @Test
    fun testDefaultFavoritesInitialization() {
        manager.setInstalledApps(sampleApps)

        val favorites = manager.getFavorites()
        assertEquals(AppLibraryManager.DEFAULT_FAVORITES_COUNT, favorites.size)
        assertEquals("Netflix", favorites[0].label)
        assertEquals("YouTube", favorites[1].label)
        assertTrue(manager.isFavorite("com.netflix.ninja"))
    }

    @Test
    fun testPinAndUnpinFavorites() {
        manager.setInstalledApps(sampleApps)

        // VLC is the 7th item, so it wasn't in the default 6 favorites
        assertFalse(manager.isFavorite("org.videolan.vlc"))

        val pinned = manager.pinToFavorites("org.videolan.vlc")
        assertTrue(pinned)
        assertTrue(manager.isFavorite("org.videolan.vlc"))

        // Duplicate pin should return false
        assertFalse(manager.pinToFavorites("org.videolan.vlc"))

        // Unpin
        val unpinned = manager.removeFromFavorites("org.videolan.vlc")
        assertTrue(unpinned)
        assertFalse(manager.isFavorite("org.videolan.vlc"))
    }

    @Test
    fun testMoveFavoriteOrder() {
        manager.setInstalledApps(sampleApps)
        val initialFavs = manager.getFavorites()
        assertEquals("Netflix", initialFavs[0].label)
        assertEquals("YouTube", initialFavs[1].label)

        // Swap position 0 and 1
        val moved = manager.moveFavorite(0, 1)
        assertTrue(moved)

        val updatedFavs = manager.getFavorites()
        assertEquals("YouTube", updatedFavs[0].label)
        assertEquals("Netflix", updatedFavs[1].label)

        // Check storage updated
        assertEquals(listOf("com.google.android.youtube.tv", "com.netflix.ninja"), storage.savedFavorites?.take(2))
    }

    @Test
    fun testInvalidMoveIndices() {
        manager.setInstalledApps(sampleApps)
        assertFalse(manager.moveFavorite(-1, 0))
        assertFalse(manager.moveFavorite(0, 999))
        assertFalse(manager.moveFavorite(2, 2))
    }

    @Test
    fun testHideAndUnhideApp() {
        manager.setInstalledApps(sampleApps)
        assertFalse(manager.isHidden("com.netflix.ninja"))

        manager.hideApp("com.netflix.ninja")
        assertTrue(manager.isHidden("com.netflix.ninja"))

        // Hidden app must not appear in favorites dock
        val favs = manager.getFavorites()
        assertFalse(favs.any { it.packageName == "com.netflix.ninja" })

        // Hidden app must not appear in visible apps
        val visible = manager.getVisibleApps()
        assertFalse(visible.any { it.packageName == "com.netflix.ninja" })

        // Unhide
        manager.unhideApp("com.netflix.ninja")
        assertFalse(manager.isHidden("com.netflix.ninja"))
        assertTrue(manager.getFavorites().any { it.packageName == "com.netflix.ninja" })
    }

    @Test
    fun testCategorySections() {
        manager.setInstalledApps(sampleApps)
        val sections = manager.getCategorySections()

        // First section is Favorites Dock
        assertEquals("Favorites Dock", sections[0].title)

        // Other sections exist for categorized apps
        val titles = sections.map { it.title }
        assertTrue(titles.contains("Streaming & Video"))
        assertTrue(titles.contains("Music & Audio"))
        assertTrue(titles.contains("Games"))
        assertTrue(titles.contains("Tools & Utilities"))
        assertTrue(titles.contains("System & Settings"))
    }

    @Test
    fun testFilteredApps() {
        manager.setInstalledApps(sampleApps)

        val streaming = manager.getFilteredApps(AppCategory.STREAMING)
        assertEquals(3, streaming.size) // Netflix, YouTube, VLC

        val music = manager.getFilteredApps(AppCategory.MUSIC)
        assertEquals(1, music.size)
        assertEquals("Spotify", music[0].label)

        val all = manager.getFilteredApps(null)
        assertEquals(sampleApps.size, all.size)
    }
}
