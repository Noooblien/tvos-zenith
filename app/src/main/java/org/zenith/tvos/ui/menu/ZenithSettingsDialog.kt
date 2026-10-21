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
import org.zenith.tvos.core.settings.ZenithSettingsManager

/**
 * Glassmorphic customization modal for Zenith TV preferences.
 */
class ZenithSettingsDialog(
    context: Context,
    private val settingsManager: ZenithSettingsManager,
    private val libraryManager: AppLibraryManager,
    private val audioEngine: TactileAudioEngine,
    private val onSettingsChanged: () -> Unit,
    private val onStartScreensaverNow: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.dialog_zenith_settings)

        val btnWallpaper = findViewById<Button>(R.id.btnSettingWallpaper)
        val btnSound = findViewById<Button>(R.id.btnSettingSound)
        val btnScreensaver = findViewById<Button>(R.id.btnSettingScreensaver)
        val btnPreviewScreensaver = findViewById<Button>(R.id.btnPreviewScreensaver)
        val btnUnhideAll = findViewById<Button>(R.id.btnUnhideAll)
        val btnClose = findViewById<Button>(R.id.btnCloseSettings)

        fun refreshLabels() {
            btnWallpaper.text = "🎨 Wallpaper: ${settingsManager.wallpaperMode.displayName}"
            btnSound.text = "🔊 Tactile Audio Clicks: ${if (settingsManager.isSoundEnabled) "ON" else "OFF"}"
            btnScreensaver.text = "🌙 Ambient Screensaver: ${settingsManager.formatScreensaverTimeout()}"
            btnUnhideAll.text = "👁️ Restore Hidden Apps (${libraryManager.getHiddenCount()})"
        }

        refreshLabels()

        val menuButtons = listOf(btnWallpaper, btnSound, btnScreensaver, btnPreviewScreensaver, btnUnhideAll)
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

        btnSound.setOnClickListener {
            val enabled = settingsManager.toggleSound()
            audioEngine.isEnabled = enabled
            if (enabled) audioEngine.playSelectPop()
            refreshLabels()
            onSettingsChanged()
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
