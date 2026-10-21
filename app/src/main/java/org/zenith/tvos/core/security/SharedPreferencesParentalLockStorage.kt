package org.zenith.tvos.core.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Android SharedPreferences backed storage for persisting salted SHA-256 PIN digest
 * and locked application package names.
 */
class SharedPreferencesParentalLockStorage(context: Context) : ParentalLockStorage {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun savePinHash(hash: String) {
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    override fun loadPinHash(): String? {
        return prefs.getString(KEY_PIN_HASH, null)
    }

    override fun saveLockedPackages(packageNames: Set<String>) {
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, packageNames).apply()
    }

    override fun loadLockedPackages(): Set<String>? {
        return prefs.getStringSet(KEY_LOCKED_PACKAGES, null)
    }

    companion object {
        private const val PREFS_NAME = "tvos_zenith_parental_lock_prefs"
        private const val KEY_PIN_HASH = "salted_sha256_pin_hash"
        private const val KEY_LOCKED_PACKAGES = "locked_package_names"
    }
}
