package org.zenith.tvos.ui.topshelf

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Apple TV Top Shelf dynamic hero container.
 * Displays high-impact cinematic backdrop, metadata badges, quick-action buttons,
 * and responds to horizontal D-pad parallax motion.
 */
class TopShelfHeroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val metaBadgeView: TextView
    private val synopsisView: TextView
    private val actionContainer: LinearLayout

    init {
        // Deep obsidian background with rounded container
        val bgDrawable = GradientDrawable().apply {
            cornerRadius = 32f
            colors = intArrayOf(Color.parseColor("#1C2436"), Color.parseColor("#0F1420"))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            setStroke(2, Color.parseColor("#1FFFFFFF"))
        }
        background = bgDrawable
        setPadding(48, 40, 48, 40)

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.START
        }

        metaBadgeView = TextView(context).apply {
            text = "FEATURED • 4K HDR • SPATIAL AUDIO"
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 12f
            letterSpacing = 0.12f
        }

        titleView = TextView(context).apply {
            text = "Zenith Cinematic Engine"
            setTextColor(Color.parseColor("#F5F5F7"))
            textSize = 34f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }

        synopsisView = TextView(context).apply {
            text = "Ultra-fluid, ad-free Apple TV aesthetic built exclusively for legacy Android TV hardware."
            setTextColor(Color.parseColor("#D1D1D6"))
            textSize = 14f
            maxLines = 2
        }

        actionContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        val playBtn = Button(context).apply {
            text = "▶ Play"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            textSize = 13f
            isFocusable = true
        }

        val detailsBtn = Button(context).apply {
            text = "Details"
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 13f
            isFocusable = true
        }

        actionContainer.addView(playBtn)
        actionContainer.addView(detailsBtn)

        contentLayout.addView(metaBadgeView)
        contentLayout.addView(titleView)
        contentLayout.addView(synopsisView)
        contentLayout.addView(actionContainer)

        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
        }
        addView(contentLayout, params)
    }

    /**
     * Dynamically updates the Top Shelf content when the user hovers over an app card.
     */
    fun updateHero(title: String, subtitle: String, meta: String) {
        animate().alpha(0.4f).setDuration(80L).withEndAction {
            titleView.text = title
            synopsisView.text = subtitle
            metaBadgeView.text = meta
            animate().alpha(1.0f).setDuration(120L).start()
        }.start()
    }

    /**
     * Applies subtle parallax translation to hero container during D-pad carousel navigation.
     */
    fun applyParallax(translationX: Float) {
        animate()
            .translationX(translationX)
            .setDuration(150L)
            .start()
    }
}
