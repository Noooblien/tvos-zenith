package org.zenith.tvos.ui.menu

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.zenith.tvos.R
import org.zenith.tvos.core.audio.TactileAudioEngine
import org.zenith.tvos.core.security.ParentalLockEngine

/**
 * Apple TV Frosted Glass 4-Digit Parental PIN Entry & Change Modal.
 *
 * Modes:
 * - [Mode.VERIFY]: Prompts for the 4-digit PIN before launching or unlocking a restricted app.
 * - [Mode.CHANGE_PIN]: Two-step flow (Verify Current PIN -> Enter New 4-Digit PIN).
 */
class ParentalPinDialog(
    context: Context,
    private val title: String,
    private val subtitle: String,
    private val lockEngine: ParentalLockEngine,
    private val audioEngine: TactileAudioEngine,
    private val mode: Mode = Mode.VERIFY,
    private val onVerified: () -> Unit
) : Dialog(context) {

    enum class Mode {
        VERIFY,
        CHANGE_PIN
    }

    private var enteredPin = StringBuilder()
    private var verifiedCurrentPin: String? = null
    private lateinit var tvSubtitle: TextView
    private lateinit var tvPinDots: TextView
    private lateinit var tvStatusMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val d = context.resources.displayMetrics.density

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = (26 * d).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * d
                setColor(Color.parseColor("#F0121824"))
                setStroke((1.5f * d).toInt(), Color.parseColor("#4DFFFFFF"))
            }
            layoutParams = ViewGroup.LayoutParams((380 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvHeader = TextView(context).apply {
            text = title
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F5F5F7"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        card.addView(tvHeader)

        tvSubtitle = TextView(context).apply {
            text = subtitle
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#99EBEBF5"))
            setPadding(0, (4 * d).toInt(), 0, (14 * d).toInt())
        }
        card.addView(tvSubtitle)

        tvPinDots = TextView(context).apply {
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#2997FF"))
            typeface = Typeface.MONOSPACE
            val hPad = (20 * d).toInt()
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
            tvPinDots,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * d).toInt()
            }
        )

        tvStatusMessage = TextView(context).apply {
            text = "Default PIN: 0000"
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        card.addView(tvStatusMessage)

        val keypadGrid = GridLayout(context).apply {
            columnCount = 3
            rowCount = 4
        }

        val keys = listOf(
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "⌫", "0", "✕"
        )

        var firstButton: Button? = null
        for (key in keys) {
            val btn = Button(context).apply {
                text = key
                textSize = 16f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_category_pill)
                isFocusable = true
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        audioEngine.playFocusTick()
                        setTextColor(Color.BLACK)
                    } else {
                        setTextColor(Color.WHITE)
                    }
                }
                setOnClickListener {
                    audioEngine.playSelectPop()
                    handleKeyPress(key)
                }
            }
            if (key == "1") firstButton = btn

            val params = GridLayout.LayoutParams().apply {
                width = (86 * d).toInt()
                height = (44 * d).toInt()
                val m = (4 * d).toInt()
                setMargins(m, m, m, m)
            }
            keypadGrid.addView(btn, params)
        }

        card.addView(keypadGrid)
        setContentView(card)
        refreshDots()
        firstButton?.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
            audioEngine.playSelectPop()
            handleKeyPress(digit)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            audioEngine.playSelectPop()
            handleKeyPress("⌫")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleKeyPress(key: String) {
        when (key) {
            "✕" -> dismiss()
            "⌫" -> {
                if (enteredPin.isNotEmpty()) {
                    enteredPin.deleteCharAt(enteredPin.length - 1)
                    refreshDots()
                }
            }
            else -> {
                if (lockEngine.isCoolingDown()) {
                    val waitSec = lockEngine.getRemainingCooldownSeconds()
                    tvStatusMessage.setTextColor(Color.parseColor("#FF453A"))
                    tvStatusMessage.text = "⏳ Too many attempts. Wait ${waitSec}s"
                    return
                }
                if (enteredPin.length < ParentalLockEngine.PIN_LENGTH) {
                    enteredPin.append(key)
                    refreshDots()
                    if (enteredPin.length == ParentalLockEngine.PIN_LENGTH) {
                        evaluatePinEntry(enteredPin.toString())
                    }
                }
            }
        }
    }

    private fun evaluatePinEntry(candidate: String) {
        if (mode == Mode.VERIFY) {
            if (lockEngine.verifyPin(candidate)) {
                dismiss()
                onVerified()
            } else {
                enteredPin.clear()
                refreshDots()
                tvStatusMessage.setTextColor(Color.parseColor("#FF453A"))
                if (lockEngine.isCoolingDown()) {
                    val waitSec = lockEngine.getRemainingCooldownSeconds()
                    tvStatusMessage.text = "⏳ Locked out for ${waitSec}s"
                } else {
                    val remaining = (ParentalLockEngine.MAX_FAILED_ATTEMPTS - lockEngine.failedAttempts)
                        .coerceAtLeast(0)
                    tvStatusMessage.text = "❌ Incorrect PIN ($remaining attempts left)"
                }
            }
        } else {
            // Mode.CHANGE_PIN
            val currentVerified = verifiedCurrentPin
            if (currentVerified == null) {
                if (lockEngine.verifyPin(candidate)) {
                    verifiedCurrentPin = candidate
                    enteredPin.clear()
                    refreshDots()
                    tvSubtitle.text = "Enter your new 4-digit Parental PIN"
                    tvStatusMessage.setTextColor(Color.parseColor("#30D158"))
                    tvStatusMessage.text = "✓ Current PIN verified. Enter new 4-digit PIN"
                } else {
                    enteredPin.clear()
                    refreshDots()
                    tvStatusMessage.setTextColor(Color.parseColor("#FF453A"))
                    tvStatusMessage.text = "❌ Current PIN incorrect"
                }
            } else {
                if (lockEngine.updatePin(currentVerified, candidate)) {
                    dismiss()
                    onVerified()
                } else {
                    enteredPin.clear()
                    refreshDots()
                    tvStatusMessage.setTextColor(Color.parseColor("#FF453A"))
                    tvStatusMessage.text = "❌ Invalid new PIN format"
                }
            }
        }
    }

    private fun refreshDots() {
        val filled = enteredPin.length
        val dots = (0 until ParentalLockEngine.PIN_LENGTH).joinToString("  ") { index ->
            if (index < filled) "●" else "○"
        }
        tvPinDots.text = dots
    }
}
