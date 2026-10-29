package com.example.soundboard.audio.dsp.melody

/**
 * Time signature of a melody. Steps are sixteenth notes, so a bar of 4/4 has 16 and a bar of 6/8
 * has 12. [id] is stored in the database: never change an existing id.
 */
enum class TimeSignature(val id: String, val stepsPerBar: Int, val stepsPerBeat: Int) {
    /** Beat = quarter note. */
    FourFour("4/4", stepsPerBar = 16, stepsPerBeat = 4),

    /** Beat = dotted quarter. */
    SixEight("6/8", stepsPerBar = 12, stepsPerBeat = 6),
    ;

    companion object {
        val DEFAULT = FourFour

        /** Unknown ids fall back to [DEFAULT]. */
        fun byId(id: String?): TimeSignature = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** One note: it starts on [step] (a sixteenth-note index from the start) at MIDI pitch [midi]. */
data class Note(val step: Int, val midi: Int)

/**
 * A four-bar monophonic melody: at most one note per step, sorted by step. Every note plays the
 * sample from its start until the sample ends or the next note starts.
 *
 * Pure Kotlin (no android.*): the melody renderer and the editor both use it.
 */
data class Melody(
    val timeSignature: TimeSignature = TimeSignature.DEFAULT,
    val bpm: Int = DEFAULT_BPM,
    val notes: List<Note> = emptyList(),
) {
    init {
        require(bpm in MIN_BPM..MAX_BPM) { "bpm out of range: $bpm" }
        require(notes.all { it.step in 0 until steps && it.midi in LOWEST_MIDI..HIGHEST_MIDI }) { "note out of range" }
        require(notes.zipWithNext().all { (a, b) -> a.step < b.step }) { "notes must be sorted, one per step" }
    }

    /** Number of steps in the four bars. */
    val steps: Int get() = BARS * timeSignature.stepsPerBar

    val isEmpty: Boolean get() = notes.isEmpty()

    /** Length of one step (a sixteenth note) in seconds: one beat lasts 60 / [bpm] s. */
    val stepSeconds: Double get() = 60.0 / bpm / timeSignature.stepsPerBeat

    /** Places a note; any other note on the same step is replaced (no polyphony). */
    fun withNote(step: Int, midi: Int): Melody =
        copy(notes = (notes.filter { it.step != step } + Note(step, midi)).sortedBy { it.step })

    fun withoutNote(step: Int): Melody = copy(notes = notes.filter { it.step != step })

    fun withBpm(value: Int): Melody = copy(bpm = value.coerceIn(MIN_BPM, MAX_BPM))

    /**
     * The same melody in [signature]: each note keeps its bar and its position inside the bar;
     * notes past the end of the new, shorter bar are dropped (see [notesDroppedBy]).
     */
    fun withTimeSignature(signature: TimeSignature): Melody {
        if (signature == timeSignature) return this
        val moved = notes.mapNotNull { note ->
            val bar = note.step / timeSignature.stepsPerBar
            val position = note.step % timeSignature.stepsPerBar
            if (position < signature.stepsPerBar) Note(bar * signature.stepsPerBar + position, note.midi) else null
        }
        return Melody(signature, bpm, moved)
    }

    /** How many notes switching to [signature] would drop (ask before switching when this is > 0). */
    fun notesDroppedBy(signature: TimeSignature): Int = notes.size - withTimeSignature(signature).notes.size

    companion object {
        const val BARS = 4
        const val MIN_BPM = 60
        const val MAX_BPM = 200
        const val DEFAULT_BPM = 120

        /** C3 (MIDI 48) to C5 (MIDI 72), chromatic. */
        const val LOWEST_MIDI = 48
        const val HIGHEST_MIDI = 72
    }
}

/**
 * Text form of a melody's notes for storage: `step:midi` pairs separated by commas, e.g.
 * `0:60,4:62`. Decoding skips anything malformed or out of range, so a damaged row still loads.
 */
object NoteCodec {
    fun encode(notes: List<Note>): String = notes.joinToString(",") { "${it.step}:${it.midi}" }

    fun decode(text: String, steps: Int): List<Note> = text.split(',')
        .mapNotNull { pair ->
            val parts = pair.split(':')
            val step = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val midi = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (parts.size == 2 && step != null && midi != null && step in 0 until steps &&
                midi in Melody.LOWEST_MIDI..Melody.HIGHEST_MIDI
            ) {
                Note(step, midi)
            } else {
                null
            }
        }
        .sortedBy { it.step }
        .distinctBy { it.step }
}
