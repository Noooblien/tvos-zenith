package org.zenith.tvos.ui.dock

/**
 * High-speed offline classifier for TV applications.
 * Automatically sorts applications into categories (Streaming, Music, Gaming, Tools, System)
 * with zero network footprint or background overhead.
 */
object AppCategoryClassifier {

    private val STREAMING_PACKAGES = setOf(
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
        "com.amazon.amazonvideo.livingroom",
        "com.plexapp.android",
        "com.disney.disneyplus",
        "com.hulu.livingroomplus",
        "tv.twitch.android.app",
        "org.videolan.vlc",
        "com.kodi",
        "org.xbmc.kodi",
        "com.hbo.hbonow",
        "com.apple.atve.androidtv.appletv"
    )

    private val MUSIC_PACKAGES = setOf(
        "com.spotify.tv.android",
        "com.spotify.music",
        "com.apple.android.music",
        "deezer.android.tv",
        "com.aspiro.tidal"
    )

    private val GAMING_PACKAGES = setOf(
        "com.valvesoftware.steamlink",
        "com.nvidia.geforcenow",
        "com.limelight",
        "com.retroarch",
        "org.retroarch"
    )

    private val TOOL_PACKAGES = setOf(
        "com.lonelycatgames.xplore",
        "com.lonelycatgames.Xplore",
        "com.esotericsoftware.clouddrive",
        "com.ghisler.android.totalcommander",
        "com.ghisler.android.TotalCommander",
        "com.browser.tv"
    )

    /**
     * Determines the category of an application by its package name and label.
     */
    fun classify(packageName: String, label: String = "", isSystemApp: Boolean = false): AppCategory {
        val lowerPkg = packageName.lowercase()
        val lowerLabel = label.lowercase()

        // 1. Exact package checks (O(1) priority matching)
        if (STREAMING_PACKAGES.contains(lowerPkg)) return AppCategory.STREAMING
        if (MUSIC_PACKAGES.contains(lowerPkg)) return AppCategory.MUSIC
        if (GAMING_PACKAGES.contains(lowerPkg)) return AppCategory.GAMING
        if (TOOL_PACKAGES.contains(lowerPkg) || TOOL_PACKAGES.contains(packageName)) return AppCategory.TOOLS

        // 2. System and Settings checks
        if (lowerPkg.contains("settings") || lowerPkg.contains("android.tv.settings") || (isSystemApp && lowerLabel.contains("settings"))) {
            return AppCategory.SYSTEM
        }

        // 3. Substring heuristic matching
        if (lowerPkg.contains("video") ||
            lowerPkg.contains("cinema") ||
            lowerLabel.contains("stream") ||
            lowerLabel.endsWith(" tv") ||
            lowerLabel.startsWith("tv ")
        ) {
            return AppCategory.STREAMING
        }

        if (lowerPkg.contains(".music") ||
            lowerPkg.contains(".audio") ||
            lowerLabel.contains("music") ||
            lowerLabel.contains("audio") ||
            lowerLabel.contains("radio")
        ) {
            return AppCategory.MUSIC
        }

        if (lowerPkg.contains(".game.") ||
            lowerPkg.endsWith(".game") ||
            lowerPkg.contains("emulator") ||
            lowerLabel.contains("game") ||
            lowerLabel.contains("emulator")
        ) {
            return AppCategory.GAMING
        }

        if (lowerPkg.contains("file") ||
            lowerPkg.contains("explorer") ||
            lowerPkg.contains("terminal") ||
            lowerLabel.contains("file") ||
            lowerLabel.contains("explorer")
        ) {
            return AppCategory.TOOLS
        }

        return if (isSystemApp) AppCategory.SYSTEM else AppCategory.OTHER
    }
}
