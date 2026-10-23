package com.example.soundboard.audio

import kotlin.math.pow

/** All audio constants in one place. */
object AudioConfig {
    /**
     * 48 kHz is the native output rate on virtually all modern phones: no resampling and the
     * low-latency path is available. If AudioRecord fails to initialize at 48 kHz, change this
     * to 44_100 (do not add resampling code).
     */
    const val SAMPLE_RATE = 48_000

    const val MAX_CLIP_SECONDS = 5

    /** Maximum number of samples in a clip; the recording cap is enforced by sample count. */
    const val MAX_CLIP_SAMPLES = MAX_CLIP_SECONDS * SAMPLE_RATE

    /** Peak target / limiter ceiling in dBFS. */
    const val PEAK_TARGET_DBFS = -0.1

    /** Technical cap on simultaneous voices (preallocated pool, real-time safety only). */
    const val MAX_VOICES = 64

    /** Clips whose peak is below this level are rejected as silence (tunable). */
    const val SILENCE_THRESHOLD_DBFS = -50.0

    /** Fade-in / fade-out applied to the clip edges after recording. */
    const val EDGE_FADE_MS = 5.0

    /** Shortest selection the trimmer in the editor allows. */
    const val MIN_TRIM_MS = 100
    const val MIN_TRIM_SAMPLES = SAMPLE_RATE * MIN_TRIM_MS / 1000

    /** Fade-out used when a voice is stolen because the pool is full. */
    const val STEAL_FADE_MS = 5.0

    /** Limiter look-ahead and release. */
    const val LIMITER_LOOKAHEAD_MS = 1.5
    const val LIMITER_RELEASE_MS = 80.0

    /** Frames rendered per mixer buffer. 240 frames = 5 ms at 48 kHz. */
    const val MIXER_BUFFER_FRAMES = 240

    /** Linear amplitude of [PEAK_TARGET_DBFS] (about 0.98855). */
    val PEAK_TARGET_LINEAR: Float = 10.0.pow(PEAK_TARGET_DBFS / 20.0).toFloat()
}
