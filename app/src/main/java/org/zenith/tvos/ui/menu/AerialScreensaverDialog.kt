package org.zenith.tvos.ui.menu

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.zenith.tvos.core.wallpaper.DynamicWallpaperEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen Apple TV Aerial Ambient Screensaver.
 * Cycles through procedural aurora meshes, gently drifts the OLED-safe clock,
 * and dismisses immediately on any remote key or phone touch event.
 */
class AerialScreensaverDialog(
    context: Context,
    private val wallpaperEngine: DynamicWallpaperEngine,
    private val onWakeUp: () -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var rootFrame: FrameLayout
    private lateinit var clockContainer: LinearLayout
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    private var themeIndex = 0
    private val themes = DynamicWallpaperEngine.Theme.values()

    private val tickerRunnable = object : Runnable {
        override fun run() {
            updateClockAndBackdrop()
            handler.postDelayed(this, 10_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val density = context.resources.displayMetrics.density

        rootFrame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = wallpaperEngine.createWallpaperDrawable(DynamicWallpaperEngine.Theme.COSMIC_AURORA)
        }

        clockContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            val pad = (64 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        tvTime = TextView(context).apply {
            textSize = 68f
            setTextColor(Color.parseColor("#F5F5F7"))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }

        tvDate = TextView(context).apply {
            textSize = 18f
            setTextColor(Color.parseColor("#B3F5F5F7"))
            letterSpacing = 0.08f
            setPadding(0, (4 * density).toInt(), 0, 0)
        }

        val tvHint = TextView(context).apply {
            text = "ZENITH AERIAL AMBIENT • PRESS ANY KEY TO WAKE"
            textSize = 11f
            setTextColor(Color.parseColor("#66FFFFFF"))
            letterSpacing = 0.14f
            setPadding(0, (16 * density).toInt(), 0, 0)
        }

        clockContainer.addView(tvTime)
        clockContainer.addView(tvDate)
        clockContainer.addView(tvHint)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        }

        rootFrame.addView(clockContainer, params)
        setContentView(rootFrame)

        updateClockAndBackdrop()
        handler.postDelayed(tickerRunnable, 10_000L)
    }

    private fun updateClockAndBackdrop() {
        val now = Date()
        tvTime.text = timeFormat.format(now)
        tvDate.text = dateFormat.format(now).uppercase()

        themeIndex = (themeIndex + 1) % themes.size
        rootFrame.background = wallpaperEngine.createWallpaperDrawable(themes[themeIndex])

        // Subtle OLED pixel-shift protection
        val shiftX = ((themeIndex % 3) - 1) * 18f
        val shiftY = (((themeIndex / 2) % 3) - 1) * 14f
        clockContainer.animate()
            .translationX(shiftX)
            .translationY(shiftY)
            .setDuration(3000L)
            .start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            wakeUpAndDismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            wakeUpAndDismiss()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun wakeUpAndDismiss() {
        handler.removeCallbacks(tickerRunnable)
        dismiss()
        onWakeUp()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tickerRunnable)
    }
}
