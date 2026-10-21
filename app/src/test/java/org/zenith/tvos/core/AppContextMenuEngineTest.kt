package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.menu.AppContextMenuEngine
import org.zenith.tvos.core.menu.ContextActionId
import org.zenith.tvos.ui.dock.AppCategory
import org.zenith.tvos.ui.dock.AppItem

class AppContextMenuEngineTest {

    private val engine = AppContextMenuEngine()

    @Test
    fun testResolveMenuItemsForUserApp() {
        val app = AppItem(
            packageName = "com.netflix.ninja",
            activityClassName = "com.netflix.ninja.MainActivity",
            label = "Netflix",
            isSystemApp = false,
            category = AppCategory.STREAMING
        )

        val items = engine.resolveMenuItems(app, isPinned = true, isLocked = false)
        val actionIds = items.map { it.id }

        // Must contain all 6 actions including TOGGLE_LOCK and UNINSTALL
        assertTrue(actionIds.contains(ContextActionId.TOGGLE_PIN))
        assertTrue(actionIds.contains(ContextActionId.REORDER))
        assertTrue(actionIds.contains(ContextActionId.TOGGLE_LOCK))
        assertTrue(actionIds.contains(ContextActionId.APP_INFO))
        assertTrue(actionIds.contains(ContextActionId.HIDE_APP))
        assertTrue(actionIds.contains(ContextActionId.UNINSTALL))

        val pinAction = items.first { it.id == ContextActionId.TOGGLE_PIN }
        assertEquals("Remove from Favorites", pinAction.title)

        val lockAction = items.first { it.id == ContextActionId.TOGGLE_LOCK }
        assertEquals("Lock App with PIN", lockAction.title)
    }

    @Test
    fun testResolveMenuItemsForLockedSystemApp() {
        val systemApp = AppItem(
            packageName = "com.android.tv.settings",
            activityClassName = "com.android.tv.settings.MainSettings",
            label = "Settings",
            isSystemApp = true,
            category = AppCategory.SYSTEM
        )

        val items = engine.resolveMenuItems(systemApp, isPinned = false, isLocked = true)
        val actionIds = items.map { it.id }

        // Must NOT contain UNINSTALL
        assertFalse(actionIds.contains(ContextActionId.UNINSTALL))

        val pinAction = items.first { it.id == ContextActionId.TOGGLE_PIN }
        assertEquals("Pin to Favorites", pinAction.title)

        val lockAction = items.first { it.id == ContextActionId.TOGGLE_LOCK }
        assertEquals("Unlock App (Remove PIN)", lockAction.title)
    }

    @Test
    fun testCanUninstall() {
        val userApp = AppItem("com.plexapp.android", "com.plexapp.android.MainActivity", "Plex", isSystemApp = false)
        val systemApp = AppItem("com.android.tv.settings", "com.android.tv.settings.MainSettings", "Settings", isSystemApp = true)

        assertTrue(engine.canUninstall(userApp))
        assertFalse(engine.canUninstall(systemApp))

        // Invalid package cannot be uninstalled
        val invalidApp = AppItem("invalid;pkg", "com.foo.Bar", "Bad", isSystemApp = false)
        assertFalse(engine.canUninstall(invalidApp))
    }

    @Test
    fun testInvalidPackageRejectedForIntents() {
        assertNull(engine.createAppInfoIntent("evil;pkg"))
        assertNull(engine.createUninstallIntent("evil;pkg", false))
        assertNull(engine.createUninstallIntent("com.netflix.ninja", true))
    }
}
