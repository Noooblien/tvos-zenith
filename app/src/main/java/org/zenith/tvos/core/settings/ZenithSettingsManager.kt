package org.zenith.tvos.core.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Wallpaper mode options for Zenith TV.
 */
enum class WallpaperMode(val displayName: String) {
    DYNAMIC_CATEGORY("Dynamic per Category"),
    MIDNIGHT_OBSIDIAN("Midnight Obsidian"),
    COSMIC_AURORA("Cosmic Aurora"),
    DEEP_OCEAN("Deep Ocean"),
    SOLAR_NEBULA("Solar Nebula")
}

/**
 * Storage abstraction for Zenith TV Settings (JVM unit-testable).
 */
interface SettingsStorage {
    fun getWallpaperMode(): WallpaperMode
    fun setWallpaperMode(mode: WallpaperMode)
    fun isSoundEnabled(): Boolean
    fun setSoundEnabled(enabled: Boolean)
    fun getScreensaverTimeoutSec(): Int
    fun setScreensaverTimeoutSec(seconds: Int)
}

/**
 * SharedPreferences implementation of SettingsStorage.
 */
class SharedPreferencesSettingsStorage(context: Context) : SettingsStorage {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getWallpaperMode(): WallpaperMode {
        val raw = prefs.getString(KEY_WALLPAPER_MODE, WallpaperMode.DYNAMIC_CATEGORY.name)
        return try {
            WallpaperMode.valueOf(raw ?: WallpaperMode.DYNAMIC_CATEGORY.name)
        } catch (e: Exception) {
            WallpaperMode.DYNAMIC_CATEGORY
        }
    }

    override fun setWallpaperMode(mode: WallpaperMode) {
        prefs.edit().putString(KEY_WALLPAPER_MODE, mode.name).apply()
    }

    override fun isSoundEnabled(): Boolean {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }

    override fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    override fun getScreensaverTimeoutSec(): Int {
        return prefs.getInt(KEY_SCREENSAVER_TIMEOUT, 120)
    }

    override fun setScreensaverTimeoutSec(seconds: Int) {
        prefs.edit().putInt(KEY_SCREENSAVER_TIMEOUT, seconds).apply()
    }

    companion object {
        private const val PREFS_NAME = "zenith_tv_settings_prefs"
        private const val KEY_WALLPAPER_MODE = "wallpaper_mode"
        private const val KEY_SOUND_ENABLED = "tactile_sound_enabled"
        private const val KEY_SCREENSAVER_TIMEOUT = "screensaver_timeout_sec"
    }
}

/**
 * Domain controller managing Zenith TV preferences and cycling options via remote D-pad.
 */
class ZenithSettingsManager(
    private val storage: SettingsStorage
) {
    private val timeoutOptionsSec = listOf(60, 120, 300, 0) // 1m, 2m, 5m, Off

    var wallpaperMode: WallpaperMode
        get() = storage.getWallpaperMode()
        set(value) = storage.setWallpaperMode(value)

    var isSoundEnabled: Boolean
        get() = storage.isSoundEnabled()
        set(value) = storage.setSoundEnabled(value)

    var screensaverTimeoutSec: Int
        get() = storage.getScreensaverTimeoutSec()
        set(value) = storage.setScreensaverTimeoutSec(value)

    fun cycleWallpaperMode(): WallpaperMode {
        val values = WallpaperMode.values()
        val nextIndex = (wallpaperMode.ordinal + 1) % values.size
        val next = values[nextIndex]
        wallpaperMode = next
        return next
    }

    fun toggleSound(): Boolean {
        val next = !isSoundEnabled
        isSoundEnabled = next
        return next
    }

    fun cycleScreensaverTimeout(): Int {
        val currentIdx = timeoutOptionsSec.indexOf(screensaverTimeoutSec)
        val nextIdx = if (currentIdx == -1) 0 else (currentIdx + 1) % timeoutOptionsSec.size
        val next = timeoutOptionsSec[nextIdx]
        screensaverTimeoutSec = next
        return next
    }

    fun formatScreensaverTimeout(seconds: Int = screensaverTimeoutSec): String {
        return when (seconds) {
            0 -> "Never (Off)"
            60 -> "1 Minute"
            120 -> "2 Minutes"
            300 -> "5 Minutes"
            else -> "$seconds Seconds"
        }
    }
}
