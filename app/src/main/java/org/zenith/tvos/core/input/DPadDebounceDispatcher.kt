package org.zenith.tvos.core.input

import android.view.KeyEvent

/**
 * High-performance hardware D-pad event coalescing and debouncing engine.
 * Specifically tuned for IR / Bluetooth remote controls on legacy Android TV boxes.
 *
 * Prevents navigation queue flooding, frame skipping, and focus drift when users
 * rapid-click or hold down remote navigation keys.
 */
class DPadDebounceDispatcher(
    val debounceWindowMs: Long = DEFAULT_DEBOUNCE_WINDOW_MS,
    val repeatCadenceMs: Long = DEFAULT_REPEAT_CADENCE_MS
) {

    private var lastEventTimeMs: Long = 0L
    private var lastKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN

    /**
     * Evaluates a remote KeyEvent.
     * Returns true if the key event is authorized to dispatch, or false if it was debounced/dropped.
     */
    fun shouldDispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return true
        }

        val keyCode = event.keyCode
        if (!isDirectionalKey(keyCode)) {
            return true
        }

        val now = event.eventTime
        val delta = now - lastEventTimeMs

        // Handle repeat events (user holding D-pad down)
        if (event.repeatCount > 0) {
            if (keyCode == lastKeyCode && delta < repeatCadenceMs) {
                return false
            }
            lastEventTimeMs = now
            lastKeyCode = keyCode
            return true
        }

        // Handle rapid click debounce on the same key
        if (keyCode == lastKeyCode && delta < debounceWindowMs) {
            return false
        }

        lastEventTimeMs = now
        lastKeyCode = keyCode
        return true
    }

    /**
     * Checks if key code corresponds to leanback directional navigation.
     */
    fun isDirectionalKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER -> true
            else -> false
        }
    }

    /**
     * Resets the debounce state.
     */
    fun reset() {
        lastEventTimeMs = 0L
        lastKeyCode = KeyEvent.KEYCODE_UNKNOWN
    }

    companion object {
        const val DEFAULT_DEBOUNCE_WINDOW_MS = 45L
        const val DEFAULT_REPEAT_CADENCE_MS = 80L
    }
}
