package com.example.soundboard.audio.dsp

import com.example.soundboard.audio.AudioConfig

/**
 * Pure-Kotlin mixing core: a preallocated pool of voices summed in float, then the master
 * [Limiter], then a last-resort clamp to [-1, 1].
 *
 * Not thread-safe: [play] and [render] must be called from the same (mixer) thread. The Android
 * engine hands triggers over through a lock-free queue. Nothing here allocates after construction.
 */
class MixerCore(
    private val maxVoices: Int = AudioConfig.MAX_VOICES,
    sampleRate: Int = AudioConfig.SAMPLE_RATE,
    private val limiter: Limiter = Limiter(sampleRate),
) {
    private class Voice {
        var clip: ShortArray? = null
        var pos = 0
        var order = 0L

        // Fading tail of a clip that was stolen from this slot.
        var tailClip: ShortArray? = null
        var tailPos = 0
        var tailLeft = 0
    }

    private val voices = Array(maxVoices) { Voice() }
    private val stealFadeFrames = maxOf(1, (sampleRate * AudioConfig.STEAL_FADE_MS / 1000.0).toInt())
    private var nextOrder = 0L

    /** Number of voices currently playing a clip (fading tails of stolen voices not counted). */
    var activeVoiceCount: Int = 0
        private set

    /**
     * Starts a new voice for [clip]. Always a new voice, so a clip overlaps itself instead of
     * being cut. When the pool is full the oldest voice is stolen with a short fade-out.
     */
    fun play(clip: ShortArray) {
        if (clip.isEmpty()) return
        var slot: Voice? = null
        for (v in voices) {
            if (v.clip == null) {
                slot = v
                break
            }
        }
        if (slot == null) {
            var oldest = voices[0]
            for (v in voices) if (v.order < oldest.order) oldest = v
            // The stolen clip keeps playing as a fading tail (about 5 ms) so the cut doesn't click.
            // If this slot still had an older tail fading, that one is dropped (needs >64 triggers
            // within ~5 ms to happen).
            oldest.tailClip = oldest.clip
            oldest.tailPos = oldest.pos
            oldest.tailLeft = stealFadeFrames
            slot = oldest
            activeVoiceCount--
        }
        slot.clip = clip
        slot.pos = 0
        slot.order = nextOrder++
        activeVoiceCount++
    }

    /** Renders [frames] mono samples into [out] (overwritten). */
    fun render(out: FloatArray, frames: Int) {
        out.fill(0f, 0, frames)
        for (v in voices) {
            val tail = v.tailClip
            if (tail != null) mixTail(v, tail, out, frames)
            val clip = v.clip ?: continue
            val available = clip.size - v.pos
            val count = if (available < frames) available else frames
            var p = v.pos
            for (i in 0 until count) {
                out[i] += clip[p++] * INV_SCALE
            }
            v.pos = p
            if (p >= clip.size) {
                v.clip = null
                activeVoiceCount--
            }
        }
        limiter.process(out, frames)
        for (i in 0 until frames) {
            val s = out[i]
            out[i] = if (s > 1f) 1f else if (s < -1f) -1f else s
        }
    }

    private fun mixTail(v: Voice, tail: ShortArray, out: FloatArray, frames: Int) {
        var p = v.tailPos
        var left = v.tailLeft
        var i = 0
        while (i < frames && left > 0 && p < tail.size) {
            val g = left.toFloat() / stealFadeFrames
            out[i] += tail[p++] * INV_SCALE * g
            left--
            i++
        }
        v.tailPos = p
        v.tailLeft = left
        if (left <= 0 || p >= tail.size) v.tailClip = null
    }

    /** Stops everything immediately (used when the engine is released). */
    fun clear() {
        for (v in voices) {
            v.clip = null
            v.tailClip = null
            v.tailLeft = 0
        }
        activeVoiceCount = 0
        limiter.reset()
    }

    private companion object {
        const val INV_SCALE = 1f / 32768f
    }
}
