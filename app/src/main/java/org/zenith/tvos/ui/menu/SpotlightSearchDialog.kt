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
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.zenith.tvos.R
import org.zenith.tvos.core.audio.TactileAudioEngine
import org.zenith.tvos.core.search.FuzzyAppSearchEngine
import org.zenith.tvos.ui.dock.AppItem

/**
 * Apple TV Spotlight Quick-Search & Instant Launch Modal.
 * Features a D-pad friendly virtual key bar (A-Z, 0-9, SPACE, ⌫, CLEAR)
 * and live Phone Remote keyboard text injection (`updateExternalQuery`).
 */
class SpotlightSearchDialog(
    context: Context,
    private val allVisibleApps: List<AppItem>,
    private val searchEngine: FuzzyAppSearchEngine,
    private val audioEngine: TactileAudioEngine,
    private val onLaunchApp: (AppItem) -> Unit
) : Dialog(context) {

    private var currentQuery: String = ""
    private lateinit var tvQueryDisplay: TextView
    private lateinit var tvResultCount: TextView
    private lateinit var resultsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val d = context.resources.displayMetrics.density

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * d).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * d
                setColor(Color.parseColor("#EB121824"))
                setStroke((1.5f * d).toInt(), Color.parseColor("#4DFFFFFF"))
            }
            layoutParams = ViewGroup.LayoutParams((620 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Title Header
        val tvHeader = TextView(context).apply {
            text = "🔍 Zenith Spotlight Search"
            textSize = 19f
            setTextColor(Color.parseColor("#F5F5F7"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val tvSub = TextView(context).apply {
            text = "Use D-Pad keys below or type directly from your Phone Remote (⌨️ Type)"
            textSize = 12f
            setTextColor(Color.parseColor("#99EBEBF5"))
            setPadding(0, (2 * d).toInt(), 0, (12 * d).toInt())
        }
        card.addView(tvHeader)
        card.addView(tvSub)

        // Search Input Display Box
        tvQueryDisplay = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            val hPad = (16 * d).toInt()
            val vPad = (10 * d).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * d
                setColor(Color.parseColor("#33000000"))
                setStroke((1 * d).toInt(), Color.parseColor("#662997FF"))
            }
        }
        card.addView(
            tvQueryDisplay,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * d).toInt()
            }
        )

        // Compact D-Pad Key Grid (A-Z + Backspace + Clear)
        val keyGrid = GridLayout(context).apply {
            columnCount = 10
            rowCount = 3
        }
        val keys = listOf(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
            "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
            "U", "V", "W", "X", "Y", "Z", "␣", "⌫", "CLR", "✕"
        )

        var firstKeyBtn: Button? = null
        for (key in keys) {
            val btn = Button(context).apply {
                text = key
                textSize = 11.5f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_category_pill)
                isFocusable = true
                setPadding(0, 0, 0, 0)
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
                    when (key) {
                        "␣" -> appendChar(' ')
                        "⌫" -> backspace()
                        "CLR" -> updateExternalQuery("")
                        "✕" -> dismiss()
                        else -> appendChar(key[0])
                    }
                }
            }
            if (firstKeyBtn == null) firstKeyBtn = btn
            val lp = GridLayout.LayoutParams().apply {
                width = (52 * d).toInt()
                height = (32 * d).toInt()
                setMargins((2 * d).toInt(), (2 * d).toInt(), (2 * d).toInt(), (2 * d).toInt())
            }
            keyGrid.addView(btn, lp)
        }
        card.addView(keyGrid)

        // Results Section Label
        tvResultCount = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, (12 * d).toInt(), 0, (6 * d).toInt())
        }
        card.addView(tvResultCount)

        // Scrollable Results List
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (170 * d).toInt()
            )
        }
        resultsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(resultsContainer)
        card.addView(scroll)

        setContentView(card)
        refreshResults()
        firstKeyBtn?.requestFocus()
    }

    private fun appendChar(c: Char) {
        currentQuery += c
        refreshResults()
    }

    private fun backspace() {
        if (currentQuery.isNotEmpty()) {
            currentQuery = currentQuery.dropLast(1)
            refreshResults()
        }
    }

    /**
     * Updates query from Phone Remote `/api/type` text submission.
     */
    fun updateExternalQuery(query: String) {
        currentQuery = query
        if (isShowing) {
            refreshResults()
        }
    }

    private fun refreshResults() {
        val d = context.resources.displayMetrics.density
        tvQueryDisplay.text = if (currentQuery.isEmpty()) "Type app name..." else "$currentQuery▌"

        val matches = searchEngine.search(allVisibleApps, currentQuery)
        tvResultCount.text = "MATCHING APPLICATIONS (${matches.size})"

        resultsContainer.removeAllViews()
        for (app in matches.take(8)) {
            val rowBtn = Button(context).apply {
                text = "🚀  ${app.label}   •   ${app.category.displayName}"
                textSize = 13.5f
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                val hPad = (16 * d).toInt()
                setPadding(hPad, 0, hPad, 0)
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_menu_item)
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
                    onLaunchApp(app)
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (40 * d).toInt()
            ).apply {
                bottomMargin = (6 * d).toInt()
            }
            resultsContainer.addView(rowBtn, lp)
        }
    }
}
