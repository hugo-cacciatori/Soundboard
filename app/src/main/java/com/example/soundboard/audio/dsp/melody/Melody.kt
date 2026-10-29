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

/**
 * One note: it starts on [step] (a sixteenth-note index from the start) at MIDI pitch [midi] and
 * lasts [length] steps.
 */
data class Note(val step: Int, val midi: Int, val length: Int = 1) {
    /** First step after the note. */
    val end: Int get() = step + length
}

/**
 * A four-bar monophonic melody: notes sorted by step, never overlapping in time. Every note plays
 * the sample from its start for its length (or until the sample ends).
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
        require(
            notes.all { it.step >= 0 && it.length >= 1 && it.end <= steps && it.midi in LOWEST_MIDI..HIGHEST_MIDI },
        ) { "note out of range" }
        require(notes.zipWithNext().all { (a, b) -> a.end <= b.step }) { "notes must be sorted and not overlap" }
    }

    /** Number of steps in the four bars. */
    val steps: Int get() = BARS * timeSignature.stepsPerBar

    val isEmpty: Boolean get() = notes.isEmpty()

    /** Length of one step (a sixteenth note) in seconds: one beat lasts 60 / [bpm] s. */
    val stepSeconds: Double get() = 60.0 / bpm / timeSignature.stepsPerBeat

    /** The note sounding at [step], if any. */
    fun noteAt(step: Int): Note? = notes.firstOrNull { step >= it.step && step < it.end }

    /**
     * Places a note (no polyphony): a note starting on [step] is replaced, a note still sounding
     * there is shortened to end at [step], and the new note is shortened so it ends before the
     * next one.
     */
    fun withNote(step: Int, midi: Int, length: Int = 1): Melody {
        val kept = notes.mapNotNull { note ->
            when {
                note.step == step -> null
                note.step < step && note.end > step -> note.copy(length = step - note.step)
                else -> note
            }
        }
        val room = (kept.firstOrNull { it.step > step }?.step ?: steps) - step
        return copy(notes = (kept + Note(step, midi, length.coerceIn(1, room.coerceAtLeast(1)))).sortedBy { it.step })
    }

    fun withoutNote(step: Int): Melody = copy(notes = notes.filter { it.step != step })

    /** Sets the length of the note starting on [step], kept between 1 step and the next note (or the end). */
    fun withLength(step: Int, length: Int): Melody {
        val index = notes.indexOfFirst { it.step == step }
        if (index < 0) return this
        val room = (notes.getOrNull(index + 1)?.step ?: steps) - step
        val updated = notes[index].copy(length = length.coerceIn(1, room))
        return copy(notes = notes.toMutableList().also { it[index] = updated })
    }

    fun withBpm(value: Int): Melody = copy(bpm = value.coerceIn(MIN_BPM, MAX_BPM))

    /**
     * The same melody in [signature]: each note keeps its bar and its position inside the bar;
     * notes starting past the end of the new, shorter bar are dropped (see [notesDroppedBy]).
     * Lengths are kept, shortened where they would run into the next note or past the end.
     */
    fun withTimeSignature(signature: TimeSignature): Melody {
        if (signature == timeSignature) return this
        val moved = notes.mapNotNull { note ->
            val bar = note.step / timeSignature.stepsPerBar
            val position = note.step % timeSignature.stepsPerBar
            if (position < signature.stepsPerBar) note.copy(step = bar * signature.stepsPerBar + position) else null
        }
        return Melody(signature, bpm, fitLengths(moved, BARS * signature.stepsPerBar))
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

        /** Sorted, one note per start step, each shortened to end before the next one and within [steps]. */
        internal fun fitLengths(notes: List<Note>, steps: Int): List<Note> {
            val sorted = notes.sortedBy { it.step }.distinctBy { it.step }
            return sorted.mapIndexed { i, note ->
                val room = (sorted.getOrNull(i + 1)?.step ?: steps) - note.step
                note.copy(length = note.length.coerceIn(1, room))
            }
        }
    }
}

/**
 * Text form of a melody's notes for storage: `step:midi:length` triples separated by commas, e.g.
 * `0:60:2,4:62:1`. A pair without a length (stored before notes had lengths) is 1 step long.
 * Decoding skips anything malformed or out of range and shortens overlaps, so a damaged row still loads.
 */
object NoteCodec {
    fun encode(notes: List<Note>): String = notes.joinToString(",") { "${it.step}:${it.midi}:${it.length}" }

    fun decode(text: String, steps: Int): List<Note> = Melody.fitLengths(
        text.split(',').mapNotNull { entry ->
            val parts = entry.split(':').map { it.trim().toIntOrNull() }
            val step = parts.getOrNull(0)
            val midi = parts.getOrNull(1)
            val length = if (parts.size == 3) parts[2] else 1
            if (parts.size in 2..3 && step != null && midi != null && length != null && step in 0 until steps &&
                midi in Melody.LOWEST_MIDI..Melody.HIGHEST_MIDI && length >= 1
            ) {
                Note(step, midi, length)
            } else {
                null
            }
        },
        steps,
    )
}
