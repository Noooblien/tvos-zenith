package org.zenith.tvos.ui.topshelf

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.SoundEffectConstants
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.zenith.tvos.R
import org.zenith.tvos.core.recommendation.OfflineTopShelfContentEngine

/**
 * Apple TV Top Shelf dynamic hero & multi-slide showcase carousel container.
 * Displays high-impact cinematic backdrop, metadata badges, authentic tvOS capsule buttons,
 * pagination indicator dots, and responds to horizontal D-pad parallax motion internally
 * while maintaining perfect outer border alignment.
 */
class TopShelfHeroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val contentLayout: LinearLayout
    private val titleView: TextView
    private val metaBadgeView: TextView
    private val synopsisView: TextView
    private val actionContainer: LinearLayout
    private val carouselDotsView: TextView
    val primaryActionBtn: Button
    val secondaryActionBtn: Button

    var onPlayActionClicked: (() -> Unit)? = null
    var onDetailsActionClicked: (() -> Unit)? = null

    init {
        // Pixel-aligned 24dp corner radius & 1.5dp frosted border
        setBackgroundResource(R.drawable.bg_topshelf_hero)
        clipToOutline = true

        val density = context.resources.displayMetrics.density
        val padH = (36 * density).toInt()
        val padV = (28 * density).toInt()
        setPadding(padH, padV, padH, padV)

        contentLayout = LinearLayout(context).apply {
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
            textSize = 32f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
        }

        synopsisView = TextView(context).apply {
            text = "Ultra-fluid, ad-free Apple TV aesthetic built exclusively for legacy Android TV hardware."
            setTextColor(Color.parseColor("#D1D1D6"))
            textSize = 13.5f
            maxLines = 2
        }

        val btnHeight = (42 * density).toInt()
        val btnPadH = (22 * density).toInt()
        val btnPadV = (8 * density).toInt()
        val btnGap = (16 * density).toInt()

        actionContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (18 * density).toInt(), 0, 0)
        }

        primaryActionBtn = Button(context).apply {
            id = R.id.btnTopShelfPlay
            text = "▶ Play"
            setBackgroundResource(R.drawable.bg_pill_button_primary)
            setTextColor(Color.parseColor("#0B0E14"))
            textSize = 13.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isFocusable = true
            isFocusableInTouchMode = false
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                btnHeight
            ).apply {
                marginEnd = btnGap
                rightMargin = btnGap
            }
        }

        secondaryActionBtn = Button(context).apply {
            id = R.id.btnTopShelfDetails
            text = "Details"
            setBackgroundResource(R.drawable.bg_pill_button_secondary)
            setTextColor(Color.parseColor("#F5F5F7"))
            textSize = 13.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isFocusable = true
            isFocusableInTouchMode = false
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                btnHeight
            )
        }

        carouselDotsView = TextView(context).apply {
            text = "●  ○  ○"
            setTextColor(Color.parseColor("#B3EBEBF5"))
            textSize = 12f
            letterSpacing = 0.15f
        }

        // Attach Apple TV fluid scale & tactile feedback on focus
        setupButtonFocus(primaryActionBtn, isPrimary = true)
        setupButtonFocus(secondaryActionBtn, isPrimary = false)

        primaryActionBtn.setOnClickListener {
            onPlayActionClicked?.invoke()
        }
        secondaryActionBtn.setOnClickListener {
            onDetailsActionClicked?.invoke()
        }

        actionContainer.addView(primaryActionBtn)
        actionContainer.addView(secondaryActionBtn)

        contentLayout.addView(metaBadgeView)
        contentLayout.addView(titleView)
        contentLayout.addView(synopsisView)
        contentLayout.addView(actionContainer)

        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
        }
        addView(contentLayout, params)

        val dotsParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        }
        addView(carouselDotsView, dotsParams)
    }

    private fun setupButtonFocus(button: Button, isPrimary: Boolean) {
        button.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
                view.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .translationZ(12f)
                    .setDuration(120L)
                    .start()
                button.setTextColor(Color.BLACK)
            } else {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(120L)
                    .start()
                button.setTextColor(
                    if (isPrimary) Color.parseColor("#0B0E14") else Color.parseColor("#F5F5F7")
                )
            }
        }
    }

    /**
     * Dynamically updates the Top Shelf content when the user hovers over an app card
     * or when the Top Shelf Showcase Carousel advances.
     */
    fun updateHero(
        title: String,
        subtitle: String,
        meta: String,
        primaryAction: String = "▶ Play",
        secondaryAction: String = "Details",
        carouselDots: String = "●  ○  ○"
    ) {
        contentLayout.animate().alpha(0.4f).setDuration(70L).withEndAction {
            titleView.text = title
            synopsisView.text = subtitle
            metaBadgeView.text = meta
            primaryActionBtn.text = primaryAction
            secondaryActionBtn.text = secondaryAction
            carouselDotsView.text = carouselDots
            contentLayout.animate().alpha(1.0f).setDuration(100L).start()
        }.start()
    }

    /**
     * Updates hero using structured content from OfflineTopShelfContentEngine.
     */
    fun updateHero(content: OfflineTopShelfContentEngine.HeroContent) {
        updateHero(
            title = content.title,
            subtitle = content.subtitle,
            meta = content.badgeMeta,
            primaryAction = content.primaryActionLabel,
            secondaryAction = content.secondaryActionLabel,
            carouselDots = content.formatCarouselDots()
        )
    }

    /**
     * Applies subtle internal parallax translation to contentLayout during D-pad navigation.
     * Keeps outer border anchored with pixel perfection to the bottom dock.
     */
    fun applyParallax(translationX: Float) {
        contentLayout.animate()
            .translationX(translationX)
            .setDuration(150L)
            .start()
    }
}
