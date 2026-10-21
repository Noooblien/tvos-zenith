package org.zenith.tvos.core.screensaver

/**
 * State engine monitoring user remote/phone input inactivity to trigger the
 * Apple TV style Aerial Ambient Screensaver and protect OLED/LED panels from burn-in.
 */
class IdleScreensaverEngine(
    var timeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    var lastInteractionTimestampMs: Long = timeProvider()
        private set

    var isScreensaverActive: Boolean = false
        private set

    /**
     * Records a user interaction (D-pad press, touch, or phone remote key).
     * @return true if the interaction woke the TV up from an active screensaver.
     */
    fun recordInteraction(): Boolean {
        lastInteractionTimestampMs = timeProvider()
        if (isScreensaverActive) {
            isScreensaverActive = false
            return true
        }
        return false
    }

    /**
     * Evaluates whether the idle threshold has been exceeded.
     * @return true if the screensaver just transitioned from inactive to active.
     */
    fun checkIdleState(): Boolean {
        if (timeoutMs <= 0L) return false
        val elapsed = timeProvider() - lastInteractionTimestampMs
        if (!isScreensaverActive && elapsed >= timeoutMs) {
            isScreensaverActive = true
            return true
        }
        return false
    }

    /**
     * Manually activates the ambient screensaver.
     */
    fun activateNow() {
        isScreensaverActive = true
    }

    /**
     * Dismisses the screensaver and resets the inactivity timer.
     */
    fun dismiss() {
        isScreensaverActive = false
        lastInteractionTimestampMs = timeProvider()
    }

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MS = 120_000L // 2 minutes default
    }
}
