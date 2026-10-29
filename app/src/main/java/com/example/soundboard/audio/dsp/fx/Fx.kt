package com.example.soundboard.audio.dsp.fx

import com.example.soundboard.audio.AudioConfig
import com.example.soundboard.audio.dsp.ProcessResult
import com.example.soundboard.audio.dsp.SoundProcessor

/** Stored id of [Fx.None]; a top-level const so Room's column default can use it. */
const val NO_FX_ID = "none"

/**
 * The effects a button can use. A button has exactly one (or [None]); under the hood an effect
 * can be a chain. To add one: a new entry here, plus its name and icon in the editor's FX picker.
 *
 * [id] is stored in the database: never change an existing id.
 */
enum class Fx(val id: String) {
    None(NO_FX_ID),

    /**
     * Mouse voice. A plain "pitch up one octave" that keeps the formants sounds like a human
     * singing high, not like a mouse. The cartoon-mouse/chipmunk sound comes from moving the
     * formants up too (a small vocal tract), which is what a double-speed tape does. So: double
     * speed (pitch + formants up one octave), then stretch back to the original duration, then
     * thin out the low end.
     */
    Mouseyfier("mouseyfier"),

    /** Robot voice: the whole clip flattened onto the nearest note, then a metallic comb (see [RobotVoice]). */
    Robot("robot"),

    /** Orc voice: fat and dumb, a bigger body and a slow, gruff delivery (see [OrcVoice]). */
    Orc("orc"),

    /**
     * The sample sung as a melody the user arranges. The only effect that needs more than its id:
     * the button's [com.example.soundboard.audio.dsp.melody.Melody] (see [MelodyVoice]). Without
     * one, or with an empty one, it plays dry.
     */
    Melody("melody"),
    ;

    /**
     * The processing for this effect, or null for [None] (and for [Melody] without notes).
     * Future "intensity" settings go here as parameters.
     */
    fun effect(melody: com.example.soundboard.audio.dsp.melody.Melody? = null): Effect? = when (this) {
        None -> null
        Mouseyfier -> EffectChain(
            SpeedUp2x(),
            TimeStretch(factor = 2.0),
            HighPass(cutoffHz = MOUSEY_HIGH_PASS_HZ),
        )
        Robot -> RobotVoice(
            combFeedback = ROBOT_COMB_FEEDBACK,
            ringMix = ROBOT_RING_MIX,
            holdSamples = ROBOT_HOLD_SAMPLES,
        )
        Orc -> OrcVoice(
            body = ORC_BODY,
            drawl = ORC_DRAWL,
            pitchDropSemitones = ORC_PITCH_DROP_SEMITONES,
            lowPassHz = ORC_LOW_PASS_HZ,
            growl = ORC_GROWL,
        )
        Melody -> melody?.takeUnless { it.isEmpty }?.let { MelodyVoice(it) }
    }

    companion object {
        val DEFAULT = None

        private const val MOUSEY_HIGH_PASS_HZ = 150.0

        // The Robot's grain, tuned by ear on the phone (see RobotVoice): metallic resonance (0 = off),
        // share of in-tune ring-modulator buzz (0 = off), and digital grit (1 = off; 3 holds at 16 kHz).
        private const val ROBOT_COMB_FEEDBACK = 0.6
        private const val ROBOT_RING_MIX = 0.35
        private const val ROBOT_HOLD_SAMPLES = 3

        // The Orc, tuned by ear on the phone (see OrcVoice): playback speed for the bigger body (0.8 is
        // about 4 semitones down, formants included), length of the drawl, further pitch drop of the
        // voiced parts, the low-pass that dulls it, and the share of saturated growl (0 = off).
        private const val ORC_BODY = 0.8
        private const val ORC_DRAWL = 1.12
        private const val ORC_PITCH_DROP_SEMITONES = 4.0
        private const val ORC_LOW_PASS_HZ = 3_200.0
        private const val ORC_GROWL = 0.4

        /** Unknown ids (e.g. from a newer app version) fall back to [None]. */
        fun byId(id: String?): Fx = entries.firstOrNull { it.id == id } ?: None
    }
}

/** Applies an [Fx] to a stored clip. Runs off the main thread (it can take a few hundred ms). */
object FxRenderer {
    private const val SCALE_IN = 32768f

    /**
     * Returns the clip as it should play with [fx]. [Fx.None] returns [clip] itself. The result is
     * peak-normalized to [AudioConfig.PEAK_TARGET_DBFS] like any recorded clip, so a lone sound
     * still plays at full level. If the effect leaves nothing audible, the dry clip is returned.
     * [melody] is only used by [Fx.Melody].
     */
    fun render(
        clip: ShortArray,
        fx: Fx,
        melody: com.example.soundboard.audio.dsp.melody.Melody? = null,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
    ): ShortArray {
        val effect = fx.effect(melody) ?: return clip
        if (clip.isEmpty()) return clip
        val dry = FloatArray(clip.size) { clip[it] / SCALE_IN }
        val wet = effect.process(dry, sampleRate)
        return when (val result = SoundProcessor.processFloat(wet, sampleRate)) {
            is ProcessResult.Success -> result.samples
            ProcessResult.TooQuiet -> clip
        }
    }
}
