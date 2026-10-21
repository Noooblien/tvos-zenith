package org.zenith.tvos.core.menu

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.zenith.tvos.core.security.IntentSanitizer
import org.zenith.tvos.ui.dock.AppItem

/**
 * Identifiers for tvOS long-press contextual actions.
 */
enum class ContextActionId {
    TOGGLE_PIN,
    REORDER,
    TOGGLE_LOCK,
    APP_INFO,
    HIDE_APP,
    UNINSTALL
}

/**
 * Contextual menu action item model.
 */
data class ContextMenuItem(
    val id: ContextActionId,
    val title: String,
    val iconEmoji: String,
    val isDestructive: Boolean = false
)

/**
 * Logic engine for the Apple TV style long-press context menu.
 */
class AppContextMenuEngine {

    /**
     * Resolves the list of available actions for a specific app given the library and lock state.
     */
    fun resolveMenuItems(
        item: AppItem,
        isPinned: Boolean,
        isLocked: Boolean = false
    ): List<ContextMenuItem> {
        val items = mutableListOf<ContextMenuItem>()

        // 1. Pin / Unpin
        val pinTitle = if (isPinned) "Remove from Favorites" else "Pin to Favorites"
        val pinIcon = if (isPinned) "⭐" else "📌"
        items.add(ContextMenuItem(ContextActionId.TOGGLE_PIN, pinTitle, pinIcon))

        // 2. Reorder / Move
        items.add(ContextMenuItem(ContextActionId.REORDER, "Move App", "⇄"))

        // 3. Parental PIN Lock / Unlock
        val lockTitle = if (isLocked) "Unlock App (Remove PIN)" else "Lock App with PIN"
        val lockIcon = if (isLocked) "🔓" else "🔒"
        items.add(ContextMenuItem(ContextActionId.TOGGLE_LOCK, lockTitle, lockIcon))

        // 4. App Details
        items.add(ContextMenuItem(ContextActionId.APP_INFO, "App Info", "ℹ️"))

        // 5. Hide App
        items.add(ContextMenuItem(ContextActionId.HIDE_APP, "Hide App", "👁️‍🗨️"))

        // 6. Uninstall (strictly blocked for system apps)
        if (!item.isSystemApp) {
            items.add(ContextMenuItem(ContextActionId.UNINSTALL, "Uninstall", "🗑️", isDestructive = true))
        }

        return items
    }

    /**
     * Determines whether uninstallation is permissible for an application.
     */
    fun canUninstall(item: AppItem): Boolean {
        return !item.isSystemApp && IntentSanitizer.isValidPackageName(item.packageName)
    }

    /**
     * Creates safe Android intent to view Application Details in system settings.
     */
    fun createAppInfoIntent(packageName: String): Intent? {
        if (!IntentSanitizer.isValidPackageName(packageName)) return null
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Creates safe Android intent to prompt uninstallation.
     */
    fun createUninstallIntent(packageName: String, isSystemApp: Boolean): Intent? {
        if (isSystemApp || !IntentSanitizer.isValidPackageName(packageName)) return null
        return Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
