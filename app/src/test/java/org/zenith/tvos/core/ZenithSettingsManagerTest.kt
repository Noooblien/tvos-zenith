package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.settings.SettingsStorage
import org.zenith.tvos.core.settings.WallpaperMode
import org.zenith.tvos.core.settings.ZenithSettingsManager

class ZenithSettingsManagerTest {

    private class InMemorySettingsStorage : SettingsStorage {
        private var mode: WallpaperMode = WallpaperMode.DYNAMIC_CATEGORY
        private var sound: Boolean = true
        private var timeoutSec: Int = 120
        private var hud: Boolean = false
        private var topShelfCycle: Boolean = true

        override fun getWallpaperMode(): WallpaperMode = mode
        override fun setWallpaperMode(mode: WallpaperMode) { this.mode = mode }
        override fun isSoundEnabled(): Boolean = sound
        override fun setSoundEnabled(enabled: Boolean) { this.sound = enabled }
        override fun getScreensaverTimeoutSec(): Int = timeoutSec
        override fun setScreensaverTimeoutSec(seconds: Int) { this.timeoutSec = seconds }
        override fun isHudOverlayEnabled(): Boolean = hud
        override fun setHudOverlayEnabled(enabled: Boolean) { this.hud = enabled }
        override fun isTopShelfAutoCycleEnabled(): Boolean = topShelfCycle
        override fun setTopShelfAutoCycleEnabled(enabled: Boolean) { this.topShelfCycle = enabled }
    }

    @Test
    fun `cycles through all WallpaperMode values and wraps around`() {
        val manager = ZenithSettingsManager(InMemorySettingsStorage())
        assertEquals(WallpaperMode.DYNAMIC_CATEGORY, manager.wallpaperMode)

        assertEquals(WallpaperMode.MIDNIGHT_OBSIDIAN, manager.cycleWallpaperMode())
        assertEquals(WallpaperMode.COSMIC_AURORA, manager.cycleWallpaperMode())
        assertEquals(WallpaperMode.DEEP_OCEAN, manager.cycleWallpaperMode())
        assertEquals(WallpaperMode.SOLAR_NEBULA, manager.cycleWallpaperMode())
        assertEquals(WallpaperMode.DYNAMIC_CATEGORY, manager.cycleWallpaperMode())
    }

    @Test
    fun `toggles tactile sound, HUD overlay, and Top Shelf carousel state accurately`() {
        val manager = ZenithSettingsManager(InMemorySettingsStorage())
        assertTrue(manager.isSoundEnabled)
        assertFalse(manager.isHudOverlayEnabled)
        assertTrue(manager.isTopShelfAutoCycleEnabled)

        assertFalse(manager.toggleSound())
        assertFalse(manager.isSoundEnabled)
        assertTrue(manager.toggleSound())

        assertTrue(manager.toggleHudOverlay())
        assertTrue(manager.isHudOverlayEnabled)
        assertFalse(manager.toggleHudOverlay())

        assertFalse(manager.toggleTopShelfAutoCycle())
        assertFalse(manager.isTopShelfAutoCycleEnabled)
        assertTrue(manager.toggleTopShelfAutoCycle())
        assertTrue(manager.isTopShelfAutoCycleEnabled)
    }

    @Test
    fun `cycles screensaver timeout options and formats human-readable labels`() {
        val manager = ZenithSettingsManager(InMemorySettingsStorage())
        assertEquals(120, manager.screensaverTimeoutSec)
        assertEquals("2 Minutes", manager.formatScreensaverTimeout())

        assertEquals(300, manager.cycleScreensaverTimeout())
        assertEquals("5 Minutes", manager.formatScreensaverTimeout())

        assertEquals(0, manager.cycleScreensaverTimeout())
        assertEquals("Never (Off)", manager.formatScreensaverTimeout())

        assertEquals(60, manager.cycleScreensaverTimeout())
        assertEquals("1 Minute", manager.formatScreensaverTimeout())

        assertEquals(120, manager.cycleScreensaverTimeout())
    }
}
