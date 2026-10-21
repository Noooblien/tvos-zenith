package org.zenith.tvos.ui.menu

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.Toast
import org.zenith.tvos.R
import org.zenith.tvos.core.audio.TactileAudioEngine
import org.zenith.tvos.core.library.AppLibraryManager
import org.zenith.tvos.core.security.ParentalLockEngine
import org.zenith.tvos.core.settings.ZenithSettingsManager

/**
 * Glassmorphic Zenith TV Preferences Modal.
 * Provides D-pad toggles for Wallpaper Theme, Top Shelf Showcase Carousel,
 * Tactile Audio, 60FPS Telemetry HUD, Smart RAM Boost, Parental PIN Management,
 * Ambient Screensaver timeout, and Hidden App restoration.
 */
class ZenithSettingsDialog(
    context: Context,
    private val settingsManager: ZenithSettingsManager,
    private val libraryManager: AppLibraryManager,
    private val lockEngine: ParentalLockEngine,
    private val audioEngine: TactileAudioEngine,
    private val onSettingsChanged: () -> Unit,
    private val onSmartRamBoost: () -> Unit,
    private val onChangeParentalPin: () -> Unit,
    private val onStartScreensaverNow: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.dialog_zenith_settings)

        val btnWallpaper = findViewById<Button>(R.id.btnSettingWallpaper)
        val btnTopShelfCycle = findViewById<Button>(R.id.btnSettingTopShelfCycle)
        val btnSound = findViewById<Button>(R.id.btnSettingSound)
        val btnHud = findViewById<Button>(R.id.btnSettingHud)
        val btnRamBoost = findViewById<Button>(R.id.btnSmartRamBoost)
        val btnChangeParentalPin = findViewById<Button>(R.id.btnChangeParentalPin)
        val btnScreensaver = findViewById<Button>(R.id.btnSettingScreensaver)
        val btnPreviewScreensaver = findViewById<Button>(R.id.btnPreviewScreensaver)
        val btnUnhideAll = findViewById<Button>(R.id.btnUnhideAll)
        val btnClose = findViewById<Button>(R.id.btnCloseSettings)

        fun refreshLabels() {
            btnWallpaper.text = "🎨 Wallpaper: ${settingsManager.wallpaperMode.displayName}"
            btnTopShelfCycle.text = "🎬 Top Shelf Showcase Carousel: ${if (settingsManager.isTopShelfAutoCycleEnabled) "ON" else "OFF"}"
            btnSound.text = "🔊 Tactile Audio Clicks: ${if (settingsManager.isSoundEnabled) "ON" else "OFF"}"
            btnHud.text = "📊 60FPS & RAM Telemetry HUD: ${if (settingsManager.isHudOverlayEnabled) "ON" else "OFF"}"
            btnChangeParentalPin.text = "🔐 Change Parental PIN (${lockEngine.getLockedCount()} Locked)"
            btnScreensaver.text = "🌙 Ambient Screensaver: ${settingsManager.formatScreensaverTimeout()}"
            btnUnhideAll.text = "👁️ Restore Hidden Apps (${libraryManager.getHiddenCount()})"
        }

        refreshLabels()

        val menuButtons = listOf(
            btnWallpaper,
            btnTopShelfCycle,
            btnSound,
            btnHud,
            btnRamBoost,
            btnChangeParentalPin,
            btnScreensaver,
            btnPreviewScreensaver,
            btnUnhideAll
        )
        for (btn in menuButtons) {
            btn.setOnFocusChangeListener { view, hasFocus ->
                val b = view as Button
                if (hasFocus) {
                    audioEngine.playFocusTick()
                    b.setTextColor(Color.BLACK)
                } else {
                    b.setTextColor(Color.WHITE)
                }
            }
        }

        btnWallpaper.setOnClickListener {
            settingsManager.cycleWallpaperMode()
            audioEngine.playSelectPop()
            refreshLabels()
            onSettingsChanged()
        }

        btnTopShelfCycle.setOnClickListener {
            settingsManager.toggleTopShelfAutoCycle()
            audioEngine.playSelectPop()
            refreshLabels()
            onSettingsChanged()
        }

        btnSound.setOnClickListener {
            val enabled = settingsManager.toggleSound()
            audioEngine.isEnabled = enabled
            if (enabled) audioEngine.playSelectPop()
            refreshLabels()
            onSettingsChanged()
        }

        btnHud.setOnClickListener {
            settingsManager.toggleHudOverlay()
            audioEngine.playSelectPop()
            refreshLabels()
            onSettingsChanged()
        }

        btnRamBoost.setOnClickListener {
            audioEngine.playSelectPop()
            onSmartRamBoost()
        }

        btnChangeParentalPin.setOnClickListener {
            audioEngine.playSelectPop()
            dismiss()
            onChangeParentalPin()
        }

        btnScreensaver.setOnClickListener {
            settingsManager.cycleScreensaverTimeout()
            audioEngine.playSelectPop()
            refreshLabels()
            onSettingsChanged()
        }

        btnPreviewScreensaver.setOnClickListener {
            audioEngine.playSelectPop()
            dismiss()
            onStartScreensaverNow()
        }

        btnUnhideAll.setOnClickListener {
            val restored = libraryManager.unhideAllApps()
            audioEngine.playSelectPop()
            refreshLabels()
            onSettingsChanged()
            Toast.makeText(context, "Restored $restored hidden app(s)", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            audioEngine.playSelectPop()
            dismiss()
        }

        btnWallpaper.requestFocus()
    }
}
