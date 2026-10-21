package org.zenith.tvos.core.wallpaper

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Dynamic ambient wallpaper generator for Zenith TV.
 * Renders procedural multi-stop gradients, ambient color-lerped crossfades,
 * and parallax-shifted gradient orientations without heavy 4K PNG/WebP bitmap memory bloat.
 */
class DynamicWallpaperEngine {

    enum class Theme(
        val startColorHex: String,
        val endColorHex: String,
        val displayName: String
    ) {
        MIDNIGHT_OBSIDIAN("#090C15", "#182032", "Midnight Obsidian"),
        COSMIC_AURORA("#0F172A", "#1E1B4B", "Cosmic Aurora"),
        SOLAR_ECLIPSE("#18181B", "#27272A", "Solar Eclipse"),
        DEEP_SPACE("#05050A", "#121324", "Deep Space"),
        AERIAL_EARTH("#0A192F", "#172A45", "Aerial Earth")
    }

    /**
     * Resolves a dynamic gradient orientation based on normalized horizontal scroll ratio [-1.0, 1.0]
     * to simulate ambient studio light shifting across the backdrop as the user navigates the dock.
     */
    fun resolveParallaxOrientation(normalizedRatio: Float): GradientDrawable.Orientation {
        return when {
            normalizedRatio < -0.33f -> GradientDrawable.Orientation.TL_BR
            normalizedRatio > 0.33f -> GradientDrawable.Orientation.TR_BL
            else -> GradientDrawable.Orientation.TOP_BOTTOM
        }
    }

    /**
     * Computes a two-stop interpolated color pair `[startColor, endColor]` between [fromTheme] and [toTheme]
     * at progress [fraction] in `[0.0, 1.0]`.
     */
    fun computeCrossfadeStops(fromTheme: Theme, toTheme: Theme, fraction: Float): IntArray {
        val f = fraction.coerceIn(0f, 1f)
        val startA = parseColorSafely(fromTheme.startColorHex)
        val endA = parseColorSafely(fromTheme.endColorHex)
        val startB = parseColorSafely(toTheme.startColorHex)
        val endB = parseColorSafely(toTheme.endColorHex)

        return intArrayOf(
            interpolateColor(startA, startB, f),
            interpolateColor(endA, endB, f)
        )
    }

    /**
     * Computes procedural gradient drawables without bitmap allocation.
     */
    fun createWallpaperDrawable(
        theme: Theme = Theme.MIDNIGHT_OBSIDIAN,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR
    ): GradientDrawable {
        val startColor = parseColorSafely(theme.startColorHex)
        val endColor = parseColorSafely(theme.endColorHex)

        return GradientDrawable(orientation, intArrayOf(startColor, endColor)).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
            setDither(true)
        }
    }

    /**
     * Smoothly crossfades the background of [targetView] from [fromTheme] to [toTheme]
     * over [durationMs] while applying the parallax-resolved gradient orientation.
     */
    fun animateWallpaperCrossfade(
        targetView: View,
        fromTheme: Theme,
        toTheme: Theme,
        normalizedRatio: Float = 0f,
        durationMs: Long = 260L
    ) {
        val orientation = resolveParallaxOrientation(normalizedRatio)
        if (fromTheme == toTheme || durationMs <= 0L) {
            targetView.background = createWallpaperDrawable(toTheme, orientation)
            return
        }

        val drawable = createWallpaperDrawable(fromTheme, orientation)
        targetView.background = drawable

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                drawable.colors = computeCrossfadeStops(fromTheme, toTheme, fraction)
            }
            start()
        }
    }

    /**
     * Fast integer-based color interpolation avoiding android.graphics.Color stub dependencies in unit tests.
     */
    fun interpolateColor(colorA: Int, colorB: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)

        val aA = (colorA ushr 24) and 0xFF
        val rA = (colorA ushr 16) and 0xFF
        val gA = (colorA ushr 8) and 0xFF
        val bA = colorA and 0xFF

        val aB = (colorB ushr 24) and 0xFF
        val rB = (colorB ushr 16) and 0xFF
        val gB = (colorB ushr 8) and 0xFF
        val bB = colorB and 0xFF

        val a = (aA + (aB - aA) * f).toInt().coerceIn(0, 255)
        val r = (rA + (rB - rA) * f).toInt().coerceIn(0, 255)
        val g = (gA + (gB - gA) * f).toInt().coerceIn(0, 255)
        val b = (bA + (bB - bA) * f).toInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Parses standard 6-char hex color (#RRGGBB) to 32-bit ARGB.
     */
    fun parseColorSafely(hex: String): Int {
        val clean = hex.removePrefix("#")
        return when (clean.length) {
            6 -> (0xFF shl 24) or clean.toLong(16).toInt()
            8 -> clean.toLong(16).toInt()
            else -> 0xFF090C15.toInt()
        }
    }

    /**
     * Returns an optimal theme matching the current focused app category.
     */
    fun getThemeForCategory(categoryName: String): Theme {
        return when (categoryName.uppercase()) {
            "STREAMING", "STREAMING & VIDEO" -> Theme.COSMIC_AURORA
            "MUSIC", "MUSIC & AUDIO" -> Theme.SOLAR_ECLIPSE
            "GAMING", "GAMES" -> Theme.DEEP_SPACE
            "TOOLS", "TOOLS & UTILITIES" -> Theme.AERIAL_EARTH
            else -> Theme.MIDNIGHT_OBSIDIAN
        }
    }
}
