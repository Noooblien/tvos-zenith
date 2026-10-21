package org.zenith.tvos.ui.menu

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
import org.zenith.tvos.core.screensaver.IdleScreensaverEngine
import org.zenith.tvos.core.wallpaper.DynamicWallpaperEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen Apple TV Aerial Ambient Screensaver.
 * Cycles through curated 4K Aerial vistas with 1.4s color-lerped crossfades,
 * supports D-pad Left/Right interactive vista scrubbing, D-pad Up/Down POI caption toggle,
 * and drifts the OLED-safe clock along a 2D Lissajous harmonic path.
 */
class AerialScreensaverDialog(
    context: Context,
    private val wallpaperEngine: DynamicWallpaperEngine,
    private val onWakeUp: () -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private val handler = Handler(Looper.getMainLooper())
    private val screensaverEngine = IdleScreensaverEngine()
    private lateinit var rootFrame: FrameLayout
    private lateinit var clockContainer: LinearLayout
    private lateinit var tvVistaCaption: TextView
    private lateinit var tvVistaTelemetry: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    private var vistaIndex = 0
    private var lissajousStep = 0
    private var isLocationVisible = true

    private val tickerRunnable = object : Runnable {
        override fun run() {
            advanceVista(delta = 1)
            handler.postDelayed(this, 10_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val density = context.resources.displayMetrics.density
        val initialVista = screensaverEngine.resolveVista(vistaIndex)

        rootFrame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = wallpaperEngine.createWallpaperDrawable(initialVista.theme)
        }

        clockContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            val pad = (64 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        tvVistaCaption = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#E6F5F5F7"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.04f
        }

        tvVistaTelemetry = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#8038BDF8"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.12f
            setPadding(0, (2 * density).toInt(), 0, (10 * density).toInt())
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
            text = "ZENITH AERIAL AMBIENT  •  ◀ ▶ SCRUB VISTA  •  ▲ ▼ LOCATION INFO  •  OK / BACK TO WAKE"
            textSize = 11f
            setTextColor(Color.parseColor("#66FFFFFF"))
            letterSpacing = 0.12f
            setPadding(0, (16 * density).toInt(), 0, 0)
        }

        clockContainer.addView(tvVistaCaption)
        clockContainer.addView(tvVistaTelemetry)
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

        bindVistaAndClock(initialVista, previousVista = null)
        handler.postDelayed(tickerRunnable, 10_000L)
    }

    private fun advanceVista(delta: Int) {
        val prevVista = screensaverEngine.resolveVista(vistaIndex)
        vistaIndex = screensaverEngine.stepVistaIndex(vistaIndex, delta)
        val nextVista = screensaverEngine.resolveVista(vistaIndex)
        lissajousStep++
        bindVistaAndClock(nextVista, prevVista)
    }

    private fun bindVistaAndClock(
        vista: IdleScreensaverEngine.AerialVista,
        previousVista: IdleScreensaverEngine.AerialVista?
    ) {
        val now = Date()
        tvTime.text = timeFormat.format(now)
        tvDate.text = dateFormat.format(now).uppercase()
        tvVistaCaption.text = "📍 ${vista.formatCaptionLine()}"
        tvVistaTelemetry.text = vista.formatTelemetryLine()

        if (previousVista != null && previousVista.theme != vista.theme) {
            wallpaperEngine.animateWallpaperCrossfade(
                targetView = rootFrame,
                fromTheme = previousVista.theme,
                toTheme = vista.theme,
                normalizedRatio = if (vistaIndex % 2 == 0) -0.5f else 0.5f,
                durationMs = 1_400L
            )
        } else {
            rootFrame.background = wallpaperEngine.createWallpaperDrawable(vista.theme)
        }

        val density = context.resources.displayMetrics.density
        val shift = screensaverEngine.computeOledLissajousShift(lissajousStep, density)
        clockContainer.animate()
            .translationX(shift.shiftXPx)
            .translationY(shift.shiftYPx)
            .setDuration(2_800L)
            .start()
    }

    private fun toggleLocationOverlay() {
        isLocationVisible = !isLocationVisible
        val targetAlpha = if (isLocationVisible) 1.0f else 0.0f
        tvVistaCaption.animate().alpha(targetAlpha).setDuration(220L).start()
        tvVistaTelemetry.animate().alpha(targetAlpha).setDuration(220L).start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    handler.removeCallbacks(tickerRunnable)
                    advanceVista(delta = -1)
                    handler.postDelayed(tickerRunnable, 10_000L)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    handler.removeCallbacks(tickerRunnable)
                    advanceVista(delta = 1)
                    handler.postDelayed(tickerRunnable, 10_000L)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    toggleLocationOverlay()
                    return true
                }
                else -> {
                    wakeUpAndDismiss()
                    return true
                }
            }
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
