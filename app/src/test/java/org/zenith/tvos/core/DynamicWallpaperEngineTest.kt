package org.zenith.tvos.core

import android.graphics.drawable.GradientDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.zenith.tvos.core.wallpaper.DynamicWallpaperEngine

class DynamicWallpaperEngineTest {

    @Test
    fun testCategoryThemeMapping() {
        val engine = DynamicWallpaperEngine()

        assertEquals(DynamicWallpaperEngine.Theme.COSMIC_AURORA, engine.getThemeForCategory("STREAMING"))
        assertEquals(DynamicWallpaperEngine.Theme.COSMIC_AURORA, engine.getThemeForCategory("Streaming & Video"))
        assertEquals(DynamicWallpaperEngine.Theme.SOLAR_ECLIPSE, engine.getThemeForCategory("MUSIC"))
        assertEquals(DynamicWallpaperEngine.Theme.DEEP_SPACE, engine.getThemeForCategory("GAMING"))
        assertEquals(DynamicWallpaperEngine.Theme.AERIAL_EARTH, engine.getThemeForCategory("TOOLS"))
        assertEquals(DynamicWallpaperEngine.Theme.MIDNIGHT_OBSIDIAN, engine.getThemeForCategory("UNKNOWN"))
    }

    @Test
    fun testColorInterpolationBoundaries() {
        val engine = DynamicWallpaperEngine()
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()

        // At fraction = 0.0, output should be black
        assertEquals(black, engine.interpolateColor(black, white, 0.0f))

        // At fraction = 1.0, output should be white
        assertEquals(white, engine.interpolateColor(black, white, 1.0f))

        // Clamping check: < 0f -> black, > 1f -> white
        assertEquals(black, engine.interpolateColor(black, white, -0.5f))
        assertEquals(white, engine.interpolateColor(black, white, 1.5f))
    }

    @Test
    fun testCrossfadeStopsAndParallaxOrientationResolution() {
        val engine = DynamicWallpaperEngine()
        val from = DynamicWallpaperEngine.Theme.MIDNIGHT_OBSIDIAN
        val to = DynamicWallpaperEngine.Theme.COSMIC_AURORA

        val stopsStart = engine.computeCrossfadeStops(from, to, 0.0f)
        assertEquals(2, stopsStart.size)
        assertEquals(engine.parseColorSafely(from.startColorHex), stopsStart[0])
        assertEquals(engine.parseColorSafely(from.endColorHex), stopsStart[1])

        val stopsEnd = engine.computeCrossfadeStops(from, to, 1.0f)
        assertEquals(engine.parseColorSafely(to.startColorHex), stopsEnd[0])
        assertEquals(engine.parseColorSafely(to.endColorHex), stopsEnd[1])

        assertEquals(GradientDrawable.Orientation.TL_BR, engine.resolveParallaxOrientation(-0.8f))
        assertEquals(GradientDrawable.Orientation.TOP_BOTTOM, engine.resolveParallaxOrientation(0.0f))
        assertEquals(GradientDrawable.Orientation.TR_BL, engine.resolveParallaxOrientation(0.8f))
    }

    @Test
    fun testThemesHaveValidHexCodes() {
        val engine = DynamicWallpaperEngine()
        for (theme in DynamicWallpaperEngine.Theme.values()) {
            assertNotNull(theme.displayName)
            val start = engine.parseColorSafely(theme.startColorHex)
            val end = engine.parseColorSafely(theme.endColorHex)
            assertNotNull(start)
            assertNotNull(end)
        }
    }
}
