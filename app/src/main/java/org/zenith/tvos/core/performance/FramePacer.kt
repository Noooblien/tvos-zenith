package org.zenith.tvos.core.performance

import android.view.Choreographer
import android.util.Log

/**
 * Real-time frame pacer and jank detector for TV devices.
 * Uses Choreographer callbacks to identify dropped frames on 60Hz displays (>16.6ms frame delta).
 */
class FramePacer(
    private val onJankDetected: ((droppedFrames: Int, durationMs: Long) -> Unit)? = null
) : Choreographer.FrameCallback {

    private var lastFrameTimeNanos: Long = 0L
    private var isRunning: Boolean = false

    fun start() {
        if (!isRunning) {
            isRunning = true
            lastFrameTimeNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        if (lastFrameTimeNanos != 0L) {
            val diffNanos = frameTimeNanos - lastFrameTimeNanos
            val frameTimeMs = diffNanos / 1_000_000L
            evaluateFrame(frameTimeMs)
        }

        lastFrameTimeNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    /**
     * Evaluates frame delta in milliseconds and triggers callback if jank occurs (>32ms).
     * Returns the number of dropped frames detected.
     */
    fun evaluateFrame(frameTimeMs: Long): Int {
        if (frameTimeMs > JANK_THRESHOLD_MS) {
            val droppedFrames = computeDroppedFrames(frameTimeMs)
            Log.w(TAG, "Frame drop detected: $droppedFrames frames dropped ($frameTimeMs ms)")
            onJankDetected?.invoke(droppedFrames, frameTimeMs)
            return droppedFrames
        }
        return 0
    }

    /**
     * Calculates the number of dropped frames given a frame duration in milliseconds.
     */
    fun computeDroppedFrames(frameTimeMs: Long): Int {
        if (frameTimeMs <= JANK_THRESHOLD_MS) return 0
        return (frameTimeMs / TARGET_FRAME_DURATION_MS).toInt()
    }

    companion object {
        private const val TAG = "FramePacer"
        const val TARGET_FRAME_DURATION_MS = 16.66f
        const val JANK_THRESHOLD_MS = 32L
    }
}
