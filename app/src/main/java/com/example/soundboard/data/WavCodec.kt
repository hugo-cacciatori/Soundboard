package com.example.soundboard.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal mono 16-bit PCM WAV reader/writer. Pure Kotlin (no android.*), unit-testable. */
object WavCodec {
    const val HEADER_SIZE = 44

    class Decoded(val samples: ShortArray, val sampleRate: Int)

    fun encode(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val buf = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16) // fmt chunk size
        buf.putShort(1) // PCM
        buf.putShort(1) // mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2) // byte rate
        buf.putShort(2) // block align
        buf.putShort(16) // bits per sample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in samples) buf.putShort(s)
        return buf.array()
    }

    /** Returns null when [bytes] is not a mono 16-bit PCM WAV file. */
    fun decode(bytes: ByteArray): Decoded? {
        if (bytes.size < 12) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (tag(buf, 0) != "RIFF" || tag(buf, 8) != "WAVE") return null

        var pos = 12
        var sampleRate = -1
        var formatOk = false
        while (pos + 8 <= bytes.size) {
            val id = tag(buf, pos)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            if (size < 0) return null
            when (id) {
                "fmt " -> {
                    if (size < 16 || body + 16 > bytes.size) return null
                    val format = buf.getShort(body).toInt()
                    val channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    val bits = buf.getShort(body + 14).toInt()
                    formatOk = format == 1 && channels == 1 && bits == 16
                }
                "data" -> {
                    if (!formatOk) return null
                    val available = minOf(size.toLong(), (bytes.size - body).toLong()).toInt()
                    val count = available / 2
                    val samples = ShortArray(count)
                    for (i in 0 until count) samples[i] = buf.getShort(body + i * 2)
                    return Decoded(samples, sampleRate)
                }
            }
            val next = body.toLong() + size + (size and 1)
            if (next > bytes.size) return null
            pos = next.toInt()
        }
        return null
    }

    private fun tag(buf: ByteBuffer, offset: Int): String =
        String(ByteArray(4) { buf.get(offset + it) }, Charsets.US_ASCII)
}
