package org.zenith.tvos.ui.menu

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.zenith.tvos.R
import org.zenith.tvos.core.audio.TactileAudioEngine
import org.zenith.tvos.core.focus.SpatialFocusEngine
import org.zenith.tvos.ui.dock.AppItem

/**
 * Apple TV Double-Click Home Multitasking App Switcher Carousel.
 * Displays recently launched applications in MRU order with glassmorphic cards,
 * spring-physics focus scaling, and instant one-click resume.
 */
class QuickAppSwitcherDialog(
    context: Context,
    private val recentApps: List<AppItem>,
    private val focusEngine: SpatialFocusEngine,
    private val audioEngine: TactileAudioEngine,
    private val onSelectApp: (AppItem) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val d = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (28 * d).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f * d
                setColor(Color.parseColor("#EE0E131F"))
                setStroke((1.5f * d).toInt(), Color.parseColor("#4DFFFFFF"))
            }
            layoutParams = ViewGroup.LayoutParams((680 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvHeader = TextView(context).apply {
            text = "🕘 Multitasking App Switcher"
            textSize = 20f
            setTextColor(Color.parseColor("#F5F5F7"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val tvSub = TextView(context).apply {
            text = "Recently launched applications • Use D-Pad ◀/▶ to switch, OK to resume"
            textSize = 12.5f
            setTextColor(Color.parseColor("#99EBEBF5"))
            setPadding(0, (4 * d).toInt(), 0, (18 * d).toInt())
        }

        container.addView(tvHeader)
        container.addView(tvSub)

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            val rowPad = (12 * d).toInt()
            setPadding(rowPad, rowPad, rowPad, rowPad)
        }

        var firstCard: LinearLayout? = null
        for (app in recentApps) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                val cPad = (14 * d).toInt()
                setPadding(cPad, cPad, cPad, cPad)
                background = context.getDrawable(R.drawable.card_default_background)

                val iconView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((54 * d).toInt(), (54 * d).toInt())
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    if (app.iconBitmap != null) {
                        setImageBitmap(app.iconBitmap)
                    } else {
                        setImageResource(R.drawable.ic_tvos_logo)
                    }
                }

                val labelView = TextView(context).apply {
                    text = app.label
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(0, (10 * d).toInt(), 0, (2 * d).toInt())
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }

                val badgeView = TextView(context).apply {
                    text = app.category.displayName.uppercase()
                    textSize = 9.5f
                    setTextColor(Color.parseColor("#8E8E93"))
                    gravity = Gravity.CENTER
                    maxLines = 1
                }

                addView(iconView)
                addView(labelView)
                addView(badgeView)

                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        audioEngine.playFocusTick()
                        v.background = context.getDrawable(R.drawable.card_focus_ring)
                    } else {
                        v.background = context.getDrawable(R.drawable.card_default_background)
                    }
                    focusEngine.animateFocusState(v, hasFocus)
                }

                setOnClickListener {
                    audioEngine.playSelectPop()
                    dismiss()
                    onSelectApp(app)
                }
            }

            if (firstCard == null) firstCard = card
            val lp = LinearLayout.LayoutParams((136 * d).toInt(), (132 * d).toInt()).apply {
                marginEnd = (14 * d).toInt()
            }
            row.addView(card, lp)
        }

        scroll.addView(row)
        container.addView(scroll)

        val btnClose = Button(context).apply {
            text = "Close Switcher"
            textSize = 13.5f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_category_pill)
            isFocusable = true
            setOnFocusChangeListener { v, hasFocus ->
                val b = v as Button
                if (hasFocus) {
                    audioEngine.playFocusTick()
                    b.setTextColor(Color.BLACK)
                } else {
                    b.setTextColor(Color.WHITE)
                }
            }
            setOnClickListener {
                audioEngine.playSelectPop()
                dismiss()
            }
        }

        val closeLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            (36 * d).toInt()
        ).apply {
            gravity = Gravity.END
            topMargin = (14 * d).toInt()
        }
        container.addView(btnClose, closeLp)

        setContentView(container)
        firstCard?.requestFocus()
    }
}
