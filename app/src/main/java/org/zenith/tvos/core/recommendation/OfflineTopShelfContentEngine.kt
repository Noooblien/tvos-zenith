package org.zenith.tvos.core.recommendation

import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppItem

/**
 * Air-gapped, zero-network Top Shelf dynamic content generator.
 * Produces contextual Apple TV cinematic hero metadata, actions, and badges
 * strictly computed on-device with zero web calls, telemetry, or battery drain.
 */
class OfflineTopShelfContentEngine {

    data class HeroContent(
        val title: String,
        val subtitle: String,
        val badgeMeta: String,
        val primaryActionLabel: String,
        val secondaryActionLabel: String
    )

    /**
     * Resolves rich Top Shelf metadata for any focused application.
     */
    fun resolveContent(app: AppItem): HeroContent {
        return when (app.category) {
            AppCategory.STREAMING -> HeroContent(
                title = app.label,
                subtitle = "Resume watch history or browse 4K HDR catalog.",
                badgeMeta = "STREAMING • 4K ULTRA HD • DOLBY ATMOS",
                primaryActionLabel = "▶ Watch Now",
                secondaryActionLabel = "Browse"
            )
            AppCategory.MUSIC -> HeroContent(
                title = app.label,
                subtitle = "High-fidelity audio streaming and curated playlists.",
                badgeMeta = "MUSIC • LOSSLESS AUDIO • SPATIAL",
                primaryActionLabel = "▶ Listen",
                secondaryActionLabel = "Library"
            )
            AppCategory.GAMING -> HeroContent(
                title = app.label,
                subtitle = "Ultra-low latency controller gaming and cloud streaming.",
                badgeMeta = "GAMES • 60 FPS • CONTROLLER READY",
                primaryActionLabel = "▶ Play",
                secondaryActionLabel = "Manage"
            )
            AppCategory.TOOLS -> HeroContent(
                title = app.label,
                subtitle = "Manage files, network storage, and utilities.",
                badgeMeta = "UTILITIES • LOCAL STORAGE • READY",
                primaryActionLabel = "Open",
                secondaryActionLabel = "Options"
            )
            AppCategory.SYSTEM -> HeroContent(
                title = app.label,
                subtitle = "Configure display, sound, remotes, and system preferences.",
                badgeMeta = "SYSTEM • DEVICE PREFERENCES",
                primaryActionLabel = "Open Settings",
                secondaryActionLabel = "About"
            )
            AppCategory.OTHER -> HeroContent(
                title = app.label,
                subtitle = "Launch application in ultra-high performance mode.",
                badgeMeta = "APPLICATION • AIR-GAPPED • READY",
                primaryActionLabel = "Launch",
                secondaryActionLabel = "Details"
            )
        }
    }
}
