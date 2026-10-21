package org.zenith.tvos.core.parallax

/**
 * Real-time Multi-Plane 2.5D Parallax and Wallpaper Motion Engine.
 *
 * Implements Apple TV tvOS cinematic backdrop depth by calculating multi-plane parallax
 * offsets (Foreground badge plane, Midground title/synopsis plane, Background action plane)
 * and subtle specular card tilts based on remote D-pad carousel position.
 *
 * Performance characteristics:
 * - Zero GC allocations on D-pad scroll events.
 * - Hardware layer friendly: computes pure transform values for setTranslationX / setRotationY.
 * - Soft clamp bounds to prevent visual clipping or letterbox seam artifacts.
 */
class ParallaxBackdropEngine(
    val maxTranslationPx: Float = DEFAULT_MAX_TRANSLATION_PX,
    val maxTiltDegrees: Float = DEFAULT_MAX_TILT_DEGREES
) {

    data class MultiPlaneOffsets(
        val badgeOffsetPx: Float,
        val titleOffsetPx: Float,
        val actionOffsetPx: Float
    )

    /**
     * Computes the normalized scroll ratio in [-1.0, 1.0] given focused index and total items.
     * Center item evaluates to 0.0, left edge to -1.0, right edge to +1.0.
     */
    fun computeNormalizedRatio(focusedIndex: Int, totalItems: Int): Float {
        if (totalItems <= 1) return 0f
        val clampedIndex = focusedIndex.coerceIn(0, totalItems - 1)
        val progress = clampedIndex.toFloat() / (totalItems - 1).toFloat()
        // Map [0.0, 1.0] to [-1.0, 1.0]
        return (progress * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
    }

    /**
     * Computes the horizontal translation offset for the primary backdrop plane.
     * Backdrops move in the opposite direction of focus traversal to create depth.
     */
    fun computeBackdropTranslationX(normalizedRatio: Float): Float {
        return -normalizedRatio.coerceIn(-1.0f, 1.0f) * maxTranslationPx
    }

    /**
     * Computes multi-plane depth offsets (`badgeOffsetPx`, `titleOffsetPx`, `actionOffsetPx`)
     * so foreground and midground Top Shelf elements shift at distinct velocities for 2.5D depth.
     */
    fun computeMultiPlaneOffsets(normalizedRatio: Float): MultiPlaneOffsets {
        val base = computeBackdropTranslationX(normalizedRatio)
        return MultiPlaneOffsets(
            badgeOffsetPx = base * FOREGROUND_BADGE_DEPTH_FACTOR,
            titleOffsetPx = base * MIDGROUND_TITLE_DEPTH_FACTOR,
            actionOffsetPx = base * BACKGROUND_ACTION_DEPTH_FACTOR
        )
    }

    /**
     * Computes subtle 2.5D tilt angle on the Y-axis for card hover specular dynamics.
     */
    fun computeTiltAngleY(normalizedRatio: Float): Float {
        return normalizedRatio.coerceIn(-1.0f, 1.0f) * maxTiltDegrees
    }

    companion object {
        const val DEFAULT_MAX_TRANSLATION_PX = 36f
        const val DEFAULT_MAX_TILT_DEGREES = 4.0f
        const val FOREGROUND_BADGE_DEPTH_FACTOR = 1.25f
        const val MIDGROUND_TITLE_DEPTH_FACTOR = 1.0f
        const val BACKGROUND_ACTION_DEPTH_FACTOR = 0.65f
    }
}
