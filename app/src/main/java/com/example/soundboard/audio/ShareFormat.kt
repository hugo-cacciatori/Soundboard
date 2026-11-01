package com.example.soundboard.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** The pure parts of sharing a clip (no Android), so they run in JVM tests. */
object ShareFormat {
    const val MAX_NAME_CHARS = 40

    /** Characters no file system or receiving app should see in a file name. */
    private val unsafe = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    /** "<name>.mp3", made safe for any file system; [fallback] when nothing usable is left. */
    fun fileName(name: String, fallback: String): String {
        val cleaned = name.replace(unsafe, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimStart('.')
            .take(MAX_NAME_CHARS)
            .trim()
        return (cleaned.ifEmpty { fallback }) + ".mp3"
    }

    /** A copy of [clip] scaled so its peak sits at [AudioConfig.SHARE_PEAK_DBFS]. */
    fun withHeadroom(clip: ShortArray): ShortArray {
        var peak = 0
        for (s in clip) peak = maxOf(peak, abs(s.toInt()))
        if (peak == 0) return clip.copyOf()
        val gain = 10.0.pow(AudioConfig.SHARE_PEAK_DBFS / 20.0) * 32767.0 / peak
        return ShortArray(clip.size) { (clip[it] * gain).roundToInt().coerceIn(-32767, 32767).toShort() }
    }
}
