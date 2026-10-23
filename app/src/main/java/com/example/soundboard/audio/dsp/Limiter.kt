package com.example.soundboard.audio.dsp

import com.example.soundboard.audio.AudioConfig
import kotlin.math.abs
import kotlin.math.exp

/**
 * Look-ahead peak limiter (mono, in place). Pure Kotlin, allocation-free after construction.
 *
 * Signal path per sample:
 *  1. required gain `g_req = min(1, ceiling / |x|)`
 *  2. sliding minimum of `g_req` over the last `lookahead + 1` input samples
 *  3. instant-attack / exponential-release follower (never above the sliding minimum)
 *  4. moving average over `lookahead + 1` samples (smooths the attack so there are no gain jumps)
 *  5. the gain is applied to the input delayed by `lookahead` samples
 *
 * Why this is a brick wall: for the sample `k` that leaves the delay line at time `k + L`, every
 * follower value in `[k, k + L]` is `<= g_req[k]`, so their average (the applied gain) is too.
 * A lone clip whose peak is at or below the ceiling gets a gain of exactly 1.0 and comes out
 * unchanged, delayed by [latencyFrames].
 */
class Limiter(
    sampleRate: Int = AudioConfig.SAMPLE_RATE,
    lookaheadMs: Double = AudioConfig.LIMITER_LOOKAHEAD_MS,
    releaseMs: Double = AudioConfig.LIMITER_RELEASE_MS,
    private val ceiling: Float = AudioConfig.PEAK_TARGET_LINEAR,
) {
    /** Delay introduced by the look-ahead, in frames. */
    val latencyFrames: Int = maxOf(1, (sampleRate * lookaheadMs / 1000.0).toInt())

    private val window = latencyFrames + 1

    /** Per-sample coefficient of the exponential release towards unity gain. */
    private val releaseCoeff: Float =
        (1.0 - exp(-1.0 / (sampleRate * releaseMs / 1000.0))).toFloat()

    // Delay line holding the audio. The oldest sample is read before the newest is written, so
    // a ring of `latencyFrames` slots delays the signal by exactly `latencyFrames` samples.
    private val delay = FloatArray(latencyFrames)
    private var delayPos = 0

    // Monotonic deque for the sliding minimum of the required gain.
    private val dqValue = FloatArray(window + 1)
    private val dqIndex = LongArray(window + 1)
    private var dqHead = 0
    private var dqSize = 0
    private var sampleCounter = 0L

    // Release follower and moving average of the follower output.
    private var follower = 1f
    private val smoothRing = FloatArray(window) { 1f }
    private var smoothPos = 0
    private var smoothSum = window.toDouble()

    /** Processes [frames] samples of [buffer] in place. */
    fun process(buffer: FloatArray, frames: Int) {
        val cap = dqValue.size
        for (n in 0 until frames) {
            val x = buffer[n]
            val absX = abs(x)
            val required = if (absX > ceiling) ceiling / absX else 1f

            // (2) sliding minimum over the last `window` samples
            while (dqSize > 0) {
                val last = (dqHead + dqSize - 1) % cap
                if (dqValue[last] >= required) dqSize-- else break
            }
            val tail = (dqHead + dqSize) % cap
            dqValue[tail] = required
            dqIndex[tail] = sampleCounter
            dqSize++
            while (dqIndex[dqHead] < sampleCounter - latencyFrames) {
                dqHead = (dqHead + 1) % cap
                dqSize--
            }
            val windowMin = dqValue[dqHead]
            sampleCounter++

            // (3) instant attack, exponential release (capped by the sliding minimum)
            val released = follower + (1f - follower) * releaseCoeff
            follower = if (windowMin < released) windowMin else released

            // (4) moving average
            smoothSum += follower.toDouble() - smoothRing[smoothPos].toDouble()
            smoothRing[smoothPos] = follower
            smoothPos = (smoothPos + 1) % window
            if (smoothPos == 0) {
                // Resync once per lap so rounding error can never accumulate.
                var exact = 0.0
                for (v in smoothRing) exact += v
                smoothSum = exact
            }
            val gain = (smoothSum / window).toFloat()

            // (5) apply the gain to the delayed sample, then store the new sample
            val delayed = delay[delayPos]
            delay[delayPos] = x
            delayPos = (delayPos + 1) % latencyFrames
            val y = delayed * gain
            // Guards against float rounding only (an ulp or so), never audible.
            buffer[n] = if (y > ceiling) ceiling else if (y < -ceiling) -ceiling else y
        }
    }

    /** Clears all state (delay line, gain follower). */
    fun reset() {
        delay.fill(0f)
        delayPos = 0
        dqHead = 0
        dqSize = 0
        sampleCounter = 0L
        follower = 1f
        smoothRing.fill(1f)
        smoothPos = 0
        smoothSum = window.toDouble()
    }
}
