package org.zenith.tvos.core.focus

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.SoundEffectConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import org.zenith.tvos.R

/**
 * SpatialFocusEngine: The 60fps Apple TV Focus Coordinator.
 *
 * Implements the authentic tvOS card elevation, scale-up, 3D directional specular tilt,
 * multi-layer inner icon/sheen parallax, tactile click compression, and hardware-layer
 * promotion when a view gains or loses D-pad focus.
 */
class SpatialFocusEngine(
    val focusScale: Float = DEFAULT_FOCUS_SCALE,
    val animDurationMs: Long = DEFAULT_ANIM_DURATION,
    val useSpringPhysics: Boolean = true
) {
    /**
     * Multi-layer inner card parallax state for floating app icon and opposite-shifting specular sheen.
     */
    data class CardInnerLayerParallax(
        val iconTranslationX: Float,
        val iconTranslationY: Float,
        val iconScale: Float,
        val sheenTranslationX: Float,
        val sheenAlpha: Float
    )

    private val springModel = SpringPhysicsModel()
    private val decelerateInterpolator = DecelerateInterpolator(1.8f)

    val activeInterpolator: Interpolator
        get() = if (useSpringPhysics) springModel else decelerateInterpolator

    /**
     * Computes directional Y-axis 3D specular tilt angle in degrees based on horizontal D-pad step delta.
     * Moving right (`stepDelta > 0`) banks the leading card edge (+3.5 deg); moving left banks (-3.5 deg).
     */
    fun computeDirectionalTiltY(previousPosition: Int, currentPosition: Int): Float {
        if (previousPosition < 0 || currentPosition < 0 || previousPosition == currentPosition) {
            return 0f
        }
        val delta = (currentPosition - previousPosition).coerceIn(-2, 2)
        return delta * DIRECTIONAL_TILT_STEP_DEG
    }

    /**
     * Computes subtle X-axis specular pitch when a card is focused to simulate overhead studio lighting.
     */
    fun computeSpecularPitchX(hasFocus: Boolean): Float {
        return if (hasFocus) DEFAULT_SPECULAR_PITCH_X_DEG else 0f
    }

    /**
     * Computes the inner foreground icon parallax and opposite-moving specular sheen highlight
     * for a focused tvOS card.
     */
    fun computeCardInnerParallax(
        hasFocus: Boolean,
        tiltAngleY: Float,
        density: Float = 1.0f
    ): CardInnerLayerParallax {
        if (!hasFocus) {
            return CardInnerLayerParallax(
                iconTranslationX = 0f,
                iconTranslationY = 0f,
                iconScale = 1.0f,
                sheenTranslationX = 0f,
                sheenAlpha = 0f
            )
        }
        val safeDensity = density.coerceAtLeast(0.5f)
        val normalizedStep = (tiltAngleY / DIRECTIONAL_TILT_STEP_DEG).coerceIn(-2f, 2f)
        return CardInnerLayerParallax(
            iconTranslationX = normalizedStep * DEFAULT_ICON_PARALLAX_SHIFT_DP * safeDensity,
            iconTranslationY = -DEFAULT_ICON_FOCUS_LIFT_DP * safeDensity,
            iconScale = DEFAULT_ICON_FOCUS_SCALE,
            sheenTranslationX = -normalizedStep * DEFAULT_SHEEN_SHIFT_DP * safeDensity,
            sheenAlpha = DEFAULT_SHEEN_FOCUS_ALPHA
        )
    }

    /**
     * Computes the target card scale during tactile D-pad Center press compression.
     */
    fun computePressScale(isPressed: Boolean, isFocused: Boolean = true): Float {
        if (isPressed) return DEFAULT_PRESS_SCALE
        return if (isFocused) focusScale else 1.0f
    }

    /**
     * Computes the horizontal rubber-band displacement in pixels when the user presses D-pad Left/Right
     * at the boundary of a dock row (`directionSign`: -1 for left edge, +1 for right edge).
     */
    fun computeBoundaryBounceOffsetPx(directionSign: Int, density: Float = 1.0f): Float {
        val clampedSign = directionSign.coerceIn(-1, 1)
        return clampedSign * DEFAULT_BOUNDARY_BOUNCE_DP * density.coerceAtLeast(0.5f)
    }

    /**
     * Executes a hardware-accelerated Apple TV rubber-band edge recoil animation on [view]
     * when the user hits the first or last item of the horizontal dock.
     */
    fun animateBoundaryBounce(view: View, directionSign: Int) {
        if (directionSign == 0) return
        val density = view.resources.displayMetrics.density
        val offsetPx = computeBoundaryBounceOffsetPx(directionSign, density)

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        view.animate()
            .translationX(offsetPx)
            .setDuration(75L)
            .setInterpolator(decelerateInterpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.animate()
                        .translationX(0f)
                        .setDuration(140L)
                        .setInterpolator(activeInterpolator)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                view.setLayerType(View.LAYER_TYPE_NONE, null)
                            }
                        })
                        .start()
                }
            })
            .start()
    }

    /**
     * Executes a tactile tvOS micro-compression animation when D-pad Center is clicked on a card.
     */
    fun animateClickCompression(view: View, onComplete: () -> Unit) {
        val compressedScale = computePressScale(isPressed = true, isFocused = view.hasFocus())
        val reboundScale = computePressScale(isPressed = false, isFocused = view.hasFocus())

        view.animate()
            .scaleX(compressedScale)
            .scaleY(compressedScale)
            .setDuration(55L)
            .setInterpolator(decelerateInterpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.animate()
                        .scaleX(reboundScale)
                        .scaleY(reboundScale)
                        .setDuration(95L)
                        .setInterpolator(activeInterpolator)
                        .setListener(null)
                        .start()
                    onComplete()
                }
            })
            .start()
    }

    /**
     * Animates the inner floating icon layer and opposite-moving specular sheen inside a tvOS card.
     */
    fun animateCardInnerLayers(
        iconView: View?,
        sheenView: View?,
        hasFocus: Boolean,
        tiltAngleY: Float = 0f
    ) {
        val density = iconView?.resources?.displayMetrics?.density ?: 1.0f
        val inner = computeCardInnerParallax(hasFocus, tiltAngleY, density)

        iconView?.animate()
            ?.translationX(inner.iconTranslationX)
            ?.translationY(inner.iconTranslationY)
            ?.scaleX(inner.iconScale)
            ?.scaleY(inner.iconScale)
            ?.setDuration(animDurationMs)
            ?.setInterpolator(activeInterpolator)
            ?.withEndAction {
                if (hasFocus && inner.iconTranslationX != 0f) {
                    iconView.animate()
                        .translationX(0f)
                        .setDuration(110L)
                        .start()
                }
            }
            ?.start()

        sheenView?.animate()
            ?.translationX(inner.sheenTranslationX)
            ?.alpha(inner.sheenAlpha)
            ?.setDuration(animDurationMs)
            ?.setInterpolator(decelerateInterpolator)
            ?.withEndAction {
                if (hasFocus && inner.sheenTranslationX != 0f) {
                    sheenView.animate()
                        .translationX(0f)
                        .setDuration(110L)
                        .start()
                }
            }
            ?.start()
    }

    /**
     * Binds focus animation and styling to any TV leanback item view.
     */
    fun attach(
        view: View,
        tiltAngleYProvider: (() -> Float)? = null,
        onFocusChangeExtra: ((Boolean) -> Unit)? = null
    ) {
        view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
            }
            val tiltY = if (hasFocus) (tiltAngleYProvider?.invoke() ?: 0f) else 0f
            animateFocusState(v, hasFocus, tiltAngleY = tiltY)
            onFocusChangeExtra?.invoke(hasFocus)
        }
    }

    /**
     * Executes the hardware-accelerated tvOS pop and 3D specular tilt animation.
     */
    fun animateFocusState(view: View, hasFocus: Boolean, tiltAngleY: Float = 0f) {
        val targetScale = if (hasFocus) focusScale else 1.0f
        val targetElevation = if (hasFocus) 16f else 0f
        val targetTiltY = if (hasFocus) tiltAngleY else 0f
        val targetPitchX = computeSpecularPitchX(hasFocus)

        // Switch to hardware layer during animation to avoid layout re-traversals
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationZ(targetElevation)
            .rotationY(targetTiltY)
            .rotationX(targetPitchX)
            .setDuration(animDurationMs)
            .setInterpolator(activeInterpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Gently settle Y-axis bank back to 0 while keeping focused scale & elevation
                    if (hasFocus && targetTiltY != 0f) {
                        view.animate()
                            .rotationY(0f)
                            .setDuration(110L)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                                }
                            })
                            .start()
                    } else {
                        view.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                }
            })
            .start()

        // Apply tvOS highlight stroke drawable
        if (hasFocus) {
            view.setBackgroundResource(R.drawable.card_focus_ring)
        } else {
            view.setBackgroundResource(R.drawable.card_default_background)
        }
    }

    companion object {
        const val DEFAULT_FOCUS_SCALE = 1.08f
        const val DEFAULT_PRESS_SCALE = 0.94f
        const val DEFAULT_ANIM_DURATION = 150L
        const val DIRECTIONAL_TILT_STEP_DEG = 3.5f
        const val DEFAULT_SPECULAR_PITCH_X_DEG = 2.0f
        const val DEFAULT_BOUNDARY_BOUNCE_DP = 14f
        const val DEFAULT_ICON_PARALLAX_SHIFT_DP = 3.5f
        const val DEFAULT_ICON_FOCUS_LIFT_DP = 4.0f
        const val DEFAULT_ICON_FOCUS_SCALE = 1.06f
        const val DEFAULT_SHEEN_SHIFT_DP = 10.0f
        const val DEFAULT_SHEEN_FOCUS_ALPHA = 0.28f
    }
}
