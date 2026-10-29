package com.example.soundboard.ui.melody

/**
 * Name for a new melody: "Melody N" (through [format]), with N one more than the number of
 * melodies, raised until no existing melody has that name.
 */
fun nextMelodyName(existing: Collection<String>, format: (Int) -> String): String {
    val taken = existing.map { it.trim() }.toSet()
    var n = existing.size + 1
    while (format(n) in taken) n++
    return format(n)
}

private val NOTE_NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

/** Scientific pitch name of a MIDI note: 60 is "C4". Musical notation, so not translated. */
fun noteName(midi: Int): String = NOTE_NAMES[midi % 12] + (midi / 12 - 1)

/** True for the black keys of a piano (C♯, D♯, F♯, G♯, A♯). */
fun isBlackKey(midi: Int): Boolean = NOTE_NAMES[midi % 12].length > 1
