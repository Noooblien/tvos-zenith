package org.zenith.tvos.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Sandboxed internal receiver for monitoring app installs, updates, and removals.
 * Strictly non-exported (android:exported="false" in manifest).
 */
class PackageChangeReceiver(
    private val onPackageChanged: (() -> Unit)? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action == null) return

        val action = intent.action
        val packageName = intent.data?.schemeSpecificPart

        Log.d(TAG, "Package state changed: action=$action, pkg=$packageName")

        when (action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                onPackageChanged?.invoke()
            }
        }
    }

    companion object {
        private const val TAG = "PackageChangeReceiver"
    }
}
