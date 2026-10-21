package org.zenith.tvos.core.parallax

/**
 * Real-time 2.5D Parallax and Wallpaper Motion Engine.
 *
 * Implements Apple TV tvOS cinematic backdrop depth by calculating multi-plane parallax
 * offsets and subtle specular card tilts based on remote D-pad carousel position.
 *
 * Performance characteristics:
 * - Zero GC allocations on D-pad scroll events.
 * - Hardware layer friendly: computes pure transform values for setTranslationX / setRotationY.
 * - Soft clamp bounds to prevent visual clipping or letterbox seam artifacts.
 */
class ParallaxBackdropEngine(
    val maxTranslationPx: Float = 36f,
    val maxTiltDegrees: Float = 4.0f
) {

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
     * Computes the horizontal translation offset for the backdrop.
     * Backdrops move in the opposite direction of focus traversal to create depth.
     */
    fun computeBackdropTranslationX(normalizedRatio: Float): Float {
        return -normalizedRatio * maxTranslationPx
    }

    /**
     * Computes subtle 2.5D tilt angle on the Y-axis for card hover specular dynamics.
     */
    fun computeTiltAngleY(normalizedRatio: Float): Float {
        return normalizedRatio * maxTiltDegrees
    }

    companion object {
        const val DEFAULT_MAX_TRANSLATION_PX = 36f
        const val DEFAULT_MAX_TILT_DEGREES = 4.0f
    }
}
