package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.search.FuzzyAppSearchEngine
import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppItem

class FuzzyAppSearchEngineTest {

    private val engine = FuzzyAppSearchEngine()

    private val sampleApps = listOf(
        AppItem("com.netflix.ninja", "MainActivity", "Netflix", category = AppCategory.STREAMING),
        AppItem("com.google.android.youtube.tv", "MainActivity", "YouTube TV", category = AppCategory.STREAMING),
        AppItem("com.amazon.amazonvideo.livingroom", "MainActivity", "Prime Video", category = AppCategory.STREAMING),
        AppItem("com.spotify.tv.android", "MainActivity", "Spotify Music", category = AppCategory.MUSIC),
        AppItem("com.plexapp.android", "MainActivity", "Plex", category = AppCategory.STREAMING),
        AppItem("com.android.tv.settings", "MainSettings", "Settings", isSystemApp = true, category = AppCategory.SYSTEM)
    )

    @Test
    fun `empty query returns all apps unchanged when no recents or favorites`() {
        val res = engine.search(sampleApps, "   ")
        assertEquals(6, res.size)
        assertEquals("Netflix", res[0].label)
    }

    @Test
    fun `empty query prioritizes MRU recent apps and pinned favorites`() {
        val res = engine.search(
            apps = sampleApps,
            rawQuery = "",
            recentPackages = listOf("com.plexapp.android"),
            favoritePackages = setOf("com.spotify.tv.android")
        )
        assertEquals("Plex", res[0].label)
        assertEquals("Spotify Music", res[1].label)
    }

    @Test
    fun `ranks exact and prefix matches first`() {
        val res = engine.search(sampleApps, "net")
        assertEquals(1, res.size)
        assertEquals("Netflix", res[0].label)
    }

    @Test
    fun `matches word boundaries and initials`() {
        val byWord = engine.search(sampleApps, "video")
        assertTrue(byWord.isNotEmpty())
        assertEquals("Prime Video", byWord.first().label)

        val byInitials = engine.search(sampleApps, "pv")
        assertTrue(byInitials.isNotEmpty())
        assertEquals("Prime Video", byInitials.first().label)
    }

    @Test
    fun `matches ordered character subsequences like ntflx or ytb`() {
        val ntflx = engine.search(sampleApps, "ntflx")
        assertTrue(ntflx.isNotEmpty())
        assertEquals("Netflix", ntflx.first().label)

        val ytb = engine.search(sampleApps, "ytb")
        assertTrue(ytb.isNotEmpty())
        assertEquals("YouTube TV", ytb.first().label)
    }

    @Test
    fun `matches Damerau-Levenshtein typos and adjacent character transpositions`() {
        // Adjacent transposition: "netlfix" -> "netflix" (distance 1)
        assertEquals(1, engine.computeDamerauLevenshteinDistance("netlfix", "netflix"))
        val transposed = engine.search(sampleApps, "netlfix")
        assertTrue(transposed.isNotEmpty())
        assertEquals("Netflix", transposed.first().label)

        // Substitution typo on word boundary: "yuotube" -> "youtube" (distance 1)
        val youtubeTypo = engine.search(sampleApps, "yuotube")
        assertTrue(youtubeTypo.isNotEmpty())
        assertEquals("YouTube TV", youtubeTypo.first().label)

        // Distant unrelated strings return > maxDistance
        assertTrue(engine.computeDamerauLevenshteinDistance("abcdefg", "xyz", maxDistance = 2) > 2)
    }

    @Test
    fun `matches by category name and boosts recent apps on tie`() {
        val streamingApps = engine.search(
            apps = sampleApps,
            rawQuery = "streaming",
            recentPackages = listOf("com.plexapp.android")
        )
        assertTrue(streamingApps.isNotEmpty())
        assertEquals("Plex", streamingApps.first().label)
    }
}
