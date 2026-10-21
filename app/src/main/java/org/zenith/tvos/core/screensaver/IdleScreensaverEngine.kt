package org.zenith.tvos.core.screensaver

import org.zenith.tvos.core.wallpaper.DynamicWallpaperEngine
import kotlin.math.cos
import kotlin.math.sin

/**
 * State engine monitoring user remote/phone input inactivity to trigger the
 * Apple TV style Aerial Ambient Screensaver, curate 4K Aerial location POI metadata,
 * and compute non-repeating 2D Lissajous pixel-shift offsets to protect OLED/LED panels from burn-in.
 */
class IdleScreensaverEngine(
    var timeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Metadata for a curated Apple TV style Aerial vista.
     */
    data class AerialVista(
        val title: String,
        val region: String,
        val coordinates: String,
        val altitudeLabel: String,
        val theme: DynamicWallpaperEngine.Theme
    ) {
        fun formatCaptionLine(): String = "$title  •  $region"
        fun formatTelemetryLine(): String = "$coordinates  •  $altitudeLabel"
    }

    /**
     * 2D pixel-shift offset in pixels for OLED subpixel burn-in prevention.
     */
    data class OledShiftOffset(
        val shiftXPx: Float,
        val shiftYPx: Float
    )

    val vistas: List<AerialVista> = listOf(
        AerialVista(
            title = "Patagonia Glaciers",
            region = "Southern Andes, Chile",
            coordinates = "50.9423° S, 73.4068° W",
            altitudeLabel = "ALT 2,450M • 4K HDR AERIAL",
            theme = DynamicWallpaperEngine.Theme.MIDNIGHT_OBSIDIAN
        ),
        AerialVista(
            title = "Aurora Borealis",
            region = "Tromsø Archipelago, Norway",
            coordinates = "69.6492° N, 18.9553° E",
            altitudeLabel = "ALT 4,100M • 4K HDR AERIAL",
            theme = DynamicWallpaperEngine.Theme.COSMIC_AURORA
        ),
        AerialVista(
            title = "Total Solar Eclipse",
            region = "Atacama High Plateau",
            coordinates = "23.8634° S, 69.1328° W",
            altitudeLabel = "ALT 5,050M • 4K HDR AERIAL",
            theme = DynamicWallpaperEngine.Theme.SOLAR_ECLIPSE
        ),
        AerialVista(
            title = "Orbital Nightfall",
            region = "International Space Station",
            coordinates = "418 KM LOW EARTH ORBIT",
            altitudeLabel = "27,600 KM/H • EARTH OBSERVATION",
            theme = DynamicWallpaperEngine.Theme.DEEP_SPACE
        ),
        AerialVista(
            title = "Blue Coral Atoll",
            region = "Rangiroa, French Polynesia",
            coordinates = "15.1253° S, 147.6450° W",
            altitudeLabel = "ALT 1,200M • 4K HDR AERIAL",
            theme = DynamicWallpaperEngine.Theme.AERIAL_EARTH
        )
    )

    var lastInteractionTimestampMs: Long = timeProvider()
        private set

    var isScreensaverActive: Boolean = false
        private set

    /**
     * Resolves a curated [AerialVista] for any arbitrary [index] using positive modulo wrap-around.
     */
    fun resolveVista(index: Int): AerialVista {
        val size = vistas.size
        val safeIdx = ((index % size) + size) % size
        return vistas[safeIdx]
    }

    /**
     * Steps the current vista index by [delta] (e.g. -1 for D-pad Left, +1 for D-pad Right)
     * with seamless wrap-around.
     */
    fun stepVistaIndex(currentIndex: Int, delta: Int): Int {
        val size = vistas.size
        return (((currentIndex + delta) % size) + size) % size
    }

    /**
     * Computes a non-repeating 2D Lissajous harmonic pixel-shift offset (`dx`, `dy`)
     * to evenly distribute subpixel luminance wear across OLED/QD-OLED TV panels.
     */
    fun computeOledLissajousShift(stepIndex: Int, density: Float = 1.0f): OledShiftOffset {
        val safeDensity = density.coerceAtLeast(0.5f)
        val step = stepIndex.coerceAtLeast(0).toDouble()
        val dx = (MAX_OLED_SHIFT_X_DP * safeDensity * sin(step * LISSAJOUS_OMEGA_X)).toFloat()
        val dy = (MAX_OLED_SHIFT_Y_DP * safeDensity * cos(step * LISSAJOUS_OMEGA_Y)).toFloat()
        return OledShiftOffset(shiftXPx = dx, shiftYPx = dy)
    }

    /**
     * Records a user interaction (D-pad press, touch, or phone remote key).
     * @return true if the interaction woke the TV up from an active screensaver.
     */
    fun recordInteraction(): Boolean {
        lastInteractionTimestampMs = timeProvider()
        if (isScreensaverActive) {
            isScreensaverActive = false
            return true
        }
        return false
    }

    /**
     * Evaluates whether the idle threshold has been exceeded.
     * @return true if the screensaver just transitioned from inactive to active.
     */
    fun checkIdleState(): Boolean {
        if (timeoutMs <= 0L) return false
        val elapsed = timeProvider() - lastInteractionTimestampMs
        if (!isScreensaverActive && elapsed >= timeoutMs) {
            isScreensaverActive = true
            return true
        }
        return false
    }

    /**
     * Manually activates the ambient screensaver.
     */
    fun activateNow() {
        isScreensaverActive = true
    }

    /**
     * Dismisses the screensaver and resets the inactivity timer.
     */
    fun dismiss() {
        isScreensaverActive = false
        lastInteractionTimestampMs = timeProvider()
    }

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MS = 120_000L // 2 minutes default
        const val MAX_OLED_SHIFT_X_DP = 22f
        const val MAX_OLED_SHIFT_Y_DP = 16f
        private const val LISSAJOUS_OMEGA_X = 0.73
        private const val LISSAJOUS_OMEGA_Y = 0.51
    }
}
