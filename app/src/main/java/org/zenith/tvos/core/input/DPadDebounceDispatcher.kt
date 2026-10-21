package org.zenith.tvos.core.input

import android.view.KeyEvent

/**
 * High-performance hardware D-pad event coalescing, debouncing, and velocity-adaptive
 * repeat acceleration engine.
 *
 * Specifically tuned for IR / Bluetooth remote controls on legacy Android TV boxes:
 * - Prevents navigation queue flooding, frame skipping, and focus drift on rapid jitter clicks.
 * - Dynamically accelerates hold-repeat cadence as `repeatCount` grows (80ms -> 62ms -> 48ms -> 38ms)
 *   for effortless Apple TV style long-dock traversal.
 * - Detects boundary rubber-band edge bounces when attempting to navigate past index `0` or `lastIndex`.
 */
class DPadDebounceDispatcher(
    val debounceWindowMs: Long = DEFAULT_DEBOUNCE_WINDOW_MS,
    val repeatCadenceMs: Long = DEFAULT_REPEAT_CADENCE_MS
) {

    private var lastEventTimeMs: Long = 0L
    private var lastKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN

    /**
     * Computes the adaptive repeat cadence window in milliseconds based on how long
     * the user has held down a directional D-pad button (`repeatCount`).
     */
    fun computeAdaptiveRepeatCadenceMs(repeatCount: Int): Long {
        if (repeatCount <= 0) return repeatCadenceMs
        val factor = when {
            repeatCount >= 10 -> 0.475 // ~38ms (26 items/sec turbo glide)
            repeatCount >= 6 -> 0.60   // ~48ms (20 items/sec fast scroll)
            repeatCount >= 3 -> 0.775  // ~62ms (16 items/sec medium scroll)
            else -> 1.0                // 80ms initial controlled step
        }
        return (repeatCadenceMs * factor).toLong().coerceAtLeast(MIN_ADAPTIVE_REPEAT_MS)
    }

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

        // Handle repeat events (user holding D-pad down) with velocity-adaptive cadence
        if (event.repeatCount > 0) {
            val requiredCadence = computeAdaptiveRepeatCadenceMs(event.repeatCount)
            if (keyCode == lastKeyCode && delta < requiredCadence) {
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
     * Determines whether a horizontal D-pad press at [currentPosition] in a dock of [itemCount]
     * items hits the start or end boundary and should trigger an Apple TV rubber-band edge bounce.
     *
     * @return `-1` for left boundary bounce, `+1` for right boundary bounce, or `0` if not at boundary.
     */
    fun detectHorizontalBoundaryBounce(keyCode: Int, currentPosition: Int, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        return when {
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentPosition == 0 -> -1
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && currentPosition == itemCount - 1 -> 1
            else -> 0
        }
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
        const val MIN_ADAPTIVE_REPEAT_MS = 32L
    }
}
