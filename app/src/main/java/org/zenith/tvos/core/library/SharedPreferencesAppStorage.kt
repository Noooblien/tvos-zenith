package org.zenith.tvos.core.library

import android.content.Context
import android.content.SharedPreferences

/**
 * Android SharedPreferences backed storage for lightweight persistence
 * of user launcher layout, favorites dock order, hidden apps, and MRU launch history.
 */
class SharedPreferencesAppStorage(context: Context) : AppPreferencesStorage {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveFavorites(packageNames: List<String>) {
        val serialized = packageNames.joinToString(DELIMITER)
        prefs.edit().putString(KEY_FAVORITES, serialized).apply()
    }

    override fun loadFavorites(): List<String>? {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return null
        if (raw.isBlank()) return emptyList()
        return raw.split(DELIMITER).filter { it.isNotBlank() }
    }

    override fun saveHidden(packageNames: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN, packageNames).apply()
    }

    override fun loadHidden(): Set<String>? {
        return prefs.getStringSet(KEY_HIDDEN, null)
    }

    override fun saveRecentLaunches(packageNames: List<String>) {
        val serialized = packageNames.joinToString(DELIMITER)
        prefs.edit().putString(KEY_RECENTS, serialized).apply()
    }

    override fun loadRecentLaunches(): List<String>? {
        val raw = prefs.getString(KEY_RECENTS, null) ?: return null
        if (raw.isBlank()) return emptyList()
        return raw.split(DELIMITER).filter { it.isNotBlank() }
    }

    companion object {
        private const val PREFS_NAME = "tvos_zenith_library_prefs"
        private const val KEY_FAVORITES = "user_favorites_dock_order"
        private const val KEY_HIDDEN = "user_hidden_apps"
        private const val KEY_RECENTS = "user_recent_app_launches"
        private const val DELIMITER = "|"
    }
}
