package org.zenith.tvos.core.recommendation

import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppItem

/**
 * Air-gapped, zero-network Top Shelf dynamic content & multi-slide carousel generator.
 * Produces contextual Apple TV cinematic hero slides, metadata, actions, and indicator dots
 * strictly computed on-device with zero web calls, telemetry, or battery drain.
 */
class OfflineTopShelfContentEngine {

    data class HeroContent(
        val title: String,
        val subtitle: String,
        val badgeMeta: String,
        val primaryActionLabel: String,
        val secondaryActionLabel: String,
        val slideIndex: Int = 0,
        val totalSlides: Int = 1
    ) {
        /**
         * Formats Apple TV style carousel pagination dots (e.g., "● ○ ○").
         */
        fun formatCarouselDots(): String {
            if (totalSlides <= 1) return "●"
            return (0 until totalSlides).joinToString("  ") { idx ->
                if (idx == slideIndex.coerceIn(0, totalSlides - 1)) "●" else "○"
            }
        }
    }

    /**
     * Resolves a 3-slide Apple TV Top Shelf showcase carousel for any focused application.
     */
    fun resolveCarouselSlides(app: AppItem): List<HeroContent> {
        val total = 3
        return when (app.category) {
            AppCategory.STREAMING -> listOf(
                HeroContent(
                    title = app.label,
                    subtitle = "Resume watch history or browse 4K HDR catalog.",
                    badgeMeta = "STREAMING • 4K ULTRA HD • DOLBY ATMOS",
                    primaryActionLabel = "▶ Watch Now",
                    secondaryActionLabel = "Browse",
                    slideIndex = 0,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Featured Spotlight",
                    subtitle = "Critically acclaimed originals in Dolby Vision & Spatial Audio.",
                    badgeMeta = "TOP 10 TODAY • DOLBY VISION • 60 FPS",
                    primaryActionLabel = "▶ Watch Now",
                    secondaryActionLabel = "Browse",
                    slideIndex = 1,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Continue Watching",
                    subtitle = "Pick up right where you left off with instant zero-buffer playback.",
                    badgeMeta = "UP NEXT • PERSONAL QUEUE • HDR10+",
                    primaryActionLabel = "▶ Resume",
                    secondaryActionLabel = "Details",
                    slideIndex = 2,
                    totalSlides = total
                )
            )
            AppCategory.MUSIC -> listOf(
                HeroContent(
                    title = app.label,
                    subtitle = "High-fidelity audio streaming and curated playlists.",
                    badgeMeta = "MUSIC • LOSSLESS AUDIO • SPATIAL",
                    primaryActionLabel = "▶ Listen",
                    secondaryActionLabel = "Library",
                    slideIndex = 0,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Spatial Audio Mix",
                    subtitle = "Immersive multi-channel soundstage mastered for living room acoustics.",
                    badgeMeta = "DOLBY ATMOS MUSIC • 24-BIT / 192KHZ",
                    primaryActionLabel = "▶ Play Mix",
                    secondaryActionLabel = "Library",
                    slideIndex = 1,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Live Lyrics Stage",
                    subtitle = "Real-time beat-synced vocal view with ultra-clean dark glass backdrop.",
                    badgeMeta = "SING ALONG • REAL-TIME LYRICS • HIFI",
                    primaryActionLabel = "▶ Start Radio",
                    secondaryActionLabel = "Details",
                    slideIndex = 2,
                    totalSlides = total
                )
            )
            AppCategory.GAMING -> listOf(
                HeroContent(
                    title = app.label,
                    subtitle = "Ultra-low latency controller gaming and cloud streaming.",
                    badgeMeta = "GAMES • 60 FPS • CONTROLLER READY",
                    primaryActionLabel = "▶ Play",
                    secondaryActionLabel = "Manage",
                    slideIndex = 0,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Game Mode Boost",
                    subtitle = "Prioritized frame pacing and Bluetooth gamepad polling active.",
                    badgeMeta = "LOW LATENCY • 1080P/4K • DIRECT INPUT",
                    primaryActionLabel = "▶ Launch Game",
                    secondaryActionLabel = "Manage",
                    slideIndex = 1,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Couch Co-Op Ready",
                    subtitle = "Connect up to 4 wireless controllers for local multiplayer sessions.",
                    badgeMeta = "MULTIPLAYER • ARCADE • ZERO JITTER",
                    primaryActionLabel = "▶ Play Now",
                    secondaryActionLabel = "Details",
                    slideIndex = 2,
                    totalSlides = total
                )
            )
            AppCategory.TOOLS -> listOf(
                HeroContent(
                    title = app.label,
                    subtitle = "Manage files, network storage, and utilities.",
                    badgeMeta = "UTILITIES • LOCAL STORAGE • READY",
                    primaryActionLabel = "Open",
                    secondaryActionLabel = "Options",
                    slideIndex = 0,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Local & LAN Vault",
                    subtitle = "Zero-telemetry local filesystem and SMB/WebDAV media inspection.",
                    badgeMeta = "AIR-GAPPED • SANDBOX VERIFIED",
                    primaryActionLabel = "Open",
                    secondaryActionLabel = "Options",
                    slideIndex = 1,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Quick Diagnostics",
                    subtitle = "Fast storage read/write and package management tools.",
                    badgeMeta = "SYSTEM UTILITY • LEANBACK NATIVE",
                    primaryActionLabel = "Open",
                    secondaryActionLabel = "Details",
                    slideIndex = 2,
                    totalSlides = total
                )
            )
            AppCategory.SYSTEM -> listOf(
                HeroContent(
                    title = app.label,
                    subtitle = "Configure display, sound, remotes, and system preferences.",
                    badgeMeta = "SYSTEM • DEVICE PREFERENCES",
                    primaryActionLabel = "Open Settings",
                    secondaryActionLabel = "About",
                    slideIndex = 0,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Display & Audio Calibration",
                    subtitle = "Fine-tune HDMI CEC, HDR dynamic range, and digital audio passthrough.",
                    badgeMeta = "HARDWARE • HDMI-CEC • AUDIO PASSTHROUGH",
                    primaryActionLabel = "Open Settings",
                    secondaryActionLabel = "About",
                    slideIndex = 1,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Security & Network Status",
                    subtitle = "Inspect Wi-Fi link speed, Bluetooth remotes, and app permissions.",
                    badgeMeta = "SYSTEM HEALTH • OWASP PROTECTED",
                    primaryActionLabel = "Configure",
                    secondaryActionLabel = "Details",
                    slideIndex = 2,
                    totalSlides = total
                )
            )
            AppCategory.OTHER -> listOf(
                HeroContent(
                    title = app.label,
                    subtitle = "Launch application in ultra-high performance mode.",
                    badgeMeta = "APPLICATION • AIR-GAPPED • READY",
                    primaryActionLabel = "Launch",
                    secondaryActionLabel = "Details",
                    slideIndex = 0,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Hardware Accelerated",
                    subtitle = "Optimized memory footprint and 60fps surface rendering enabled.",
                    badgeMeta = "ZENITH ENGINE • LOW MEMORY OVERHEAD",
                    primaryActionLabel = "Launch",
                    secondaryActionLabel = "Details",
                    slideIndex = 1,
                    totalSlides = total
                ),
                HeroContent(
                    title = "${app.label} • Quick Access",
                    subtitle = "Long-press Select on any app card to pin, reorder, or lock with PIN.",
                    badgeMeta = "TVOS DOCK • INSTANT SWITCH",
                    primaryActionLabel = "Launch",
                    secondaryActionLabel = "Options",
                    slideIndex = 2,
                    totalSlides = total
                )
            )
        }
    }

    /**
     * Resolves a specific slide index (wrapping modulo slide count) for an application.
     */
    fun resolveSlide(app: AppItem, slideIndex: Int): HeroContent {
        val slides = resolveCarouselSlides(app)
        val safeIdx = ((slideIndex % slides.size) + slides.size) % slides.size
        return slides[safeIdx]
    }

    /**
     * Resolves primary (slide 0) Top Shelf metadata for any focused application.
     */
    fun resolveContent(app: AppItem): HeroContent {
        return resolveCarouselSlides(app).first()
    }
}
