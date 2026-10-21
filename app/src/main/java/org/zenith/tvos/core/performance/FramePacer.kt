package org.zenith.tvos.core.performance

import android.view.Choreographer
import android.util.Log
import kotlin.math.roundToInt

/**
 * Real-time frame pacer, rolling FPS telemetry tracker, and jank detector for TV devices.
 * Uses Choreographer callbacks to identify dropped frames on 60Hz displays (>16.6ms frame delta)
 * and maintains a zero-allocation ring buffer of recent frame durations for live HUD diagnostics.
 */
class FramePacer(
    private val onJankDetected: ((droppedFrames: Int, durationMs: Long) -> Unit)? = null
) : Choreographer.FrameCallback {

    private var lastFrameTimeNanos: Long = 0L
    private var isRunning: Boolean = false

    // Zero-allocation ring buffer of last 60 frame deltas (in milliseconds)
    private val frameWindowMs = LongArray(WINDOW_SIZE)
    private var windowIndex: Int = 0
    private var recordedSamples: Int = 0

    var totalFramesRendered: Long = 0L
        private set

    var totalJankFramesDropped: Long = 0L
        private set

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
            val frameTimeMs = (diffNanos / 1_000_000L).coerceAtLeast(1L)
            evaluateFrame(frameTimeMs)
        }

        lastFrameTimeNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    /**
     * Evaluates frame delta in milliseconds, records rolling telemetry, and triggers callback if jank occurs (>32ms).
     * Returns the number of dropped frames detected.
     */
    fun evaluateFrame(frameTimeMs: Long): Int {
        val clampedMs = frameTimeMs.coerceAtLeast(1L)
        frameWindowMs[windowIndex] = clampedMs
        windowIndex = (windowIndex + 1) % WINDOW_SIZE
        if (recordedSamples < WINDOW_SIZE) {
            recordedSamples++
        }
        totalFramesRendered++

        if (frameTimeMs > JANK_THRESHOLD_MS) {
            val droppedFrames = computeDroppedFrames(frameTimeMs)
            totalJankFramesDropped += droppedFrames
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

    /**
     * Computes average frame duration across the rolling window in milliseconds.
     */
    fun getAverageFrameTimeMs(): Float {
        if (recordedSamples == 0) return 16.6f
        var sum = 0L
        for (i in 0 until recordedSamples) {
            sum += frameWindowMs[i]
        }
        return sum.toFloat() / recordedSamples
    }

    /**
     * Computes current rolling frames per second (capped at 60 FPS for standard Leanback VSYNC).
     */
    fun getCurrentFps(): Int {
        if (recordedSamples == 0) return 60
        val avgMs = getAverageFrameTimeMs()
        if (avgMs <= 0f) return 60
        return (1000f / avgMs).roundToInt().coerceIn(1, 60)
    }

    /**
     * Resets telemetry counters.
     */
    fun resetStats() {
        windowIndex = 0
        recordedSamples = 0
        totalFramesRendered = 0L
        totalJankFramesDropped = 0L
    }

    companion object {
        private const val TAG = "FramePacer"
        private const val WINDOW_SIZE = 60
        const val TARGET_FRAME_DURATION_MS = 16.66f
        const val JANK_THRESHOLD_MS = 32L
    }
}
