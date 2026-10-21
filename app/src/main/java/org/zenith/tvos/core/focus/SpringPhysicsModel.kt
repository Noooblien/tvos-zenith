package org.zenith.tvos.core.focus

import android.view.animation.Interpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance, zero-allocation Spring Physics engine modeled after Apple tvOS
 * UIKit spring animations (CASpringAnimation / SwiftUI spring).
 *
 * Implements an exact closed-form damped harmonic oscillator:
 *   x(t) = 1.0 - e^(-damping * omega * t) * (cos(omega_d * t) + (damping / sqrt(1 - damping^2)) * sin(omega_d * t))
 *
 * Provides the signature Apple TV fluid "pop" and soft settle on D-pad navigation.
 */
class SpringPhysicsModel(
    val stiffness: Float = DEFAULT_STIFFNESS,
    val dampingRatio: Float = DEFAULT_DAMPING_RATIO // Underdamped for signature subtle bounce
) : Interpolator {

    private val naturalFreq = sqrt(stiffness.toDouble())
    private val dampedFreq = naturalFreq * sqrt((1.0 - (dampingRatio * dampingRatio).toDouble()).coerceIn(0.001, 1.0))
    private val decayRate = dampingRatio.toDouble() * naturalFreq

    /**
     * Computes the normalized displacement at normalized time t in [0.0, 1.0].
     * Starts at 0.0 and settles at 1.0 with a soft, damped overshoot.
     */
    fun computeDisplacement(input: Float): Float {
        val t = input.coerceIn(0f, 1f).toDouble()
        if (t <= 0.0) return 0f
        if (t >= 1.0) return 1f

        // Time scaled to dynamic settling window (~5 half-lives)
        val timeSec = t * 0.42

        val envelope = exp(-decayRate * timeSec)
        val oscillation = cos(dampedFreq * timeSec) + (decayRate / dampedFreq) * sin(dampedFreq * timeSec)

        val displacement = 1.0 - envelope * oscillation
        return displacement.toFloat()
    }

    override fun getInterpolation(input: Float): Float {
        return computeDisplacement(input)
    }

    companion object {
        const val DEFAULT_STIFFNESS = 280f
        const val DEFAULT_DAMPING_RATIO = 0.72f
    }
}
