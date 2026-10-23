package com.example.soundboard.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WavCodecTest {
    @Test
    fun roundTripKeepsSamplesAndRate() {
        val samples = shortArrayOf(0, 1, -1, 12345, -12345, Short.MAX_VALUE, Short.MIN_VALUE)
        val decoded = WavCodec.decode(WavCodec.encode(samples, 48_000))
        assertNotNull(decoded)
        assertArrayEquals(samples, decoded!!.samples)
        assertEquals(48_000, decoded.sampleRate)
    }

    @Test
    fun headerIsCanonical() {
        val bytes = WavCodec.encode(ShortArray(10), 48_000)
        assertEquals(WavCodec.HEADER_SIZE + 20, bytes.size)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(bytes.size - 8, buf.getInt(4))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals(1, buf.getShort(20).toInt()) // PCM
        assertEquals(1, buf.getShort(22).toInt()) // mono
        assertEquals(48_000, buf.getInt(24))
        assertEquals(96_000, buf.getInt(28)) // byte rate
        assertEquals(16, buf.getShort(34).toInt())
        assertEquals(20, buf.getInt(40))
    }

    @Test
    fun unknownChunksAreSkipped() {
        val base = WavCodec.encode(shortArrayOf(5, 6, 7), 44_100)
        // Insert a "LIST" chunk (odd size, so it is padded) between "fmt " and "data".
        val list = ByteArray(8 + 4) { 0 }
        "LIST".toByteArray().copyInto(list)
        list[4] = 3
        val withList = base.copyOfRange(0, 36) + list + base.copyOfRange(36, base.size)
        val decoded = WavCodec.decode(withList)
        assertNotNull(decoded)
        assertArrayEquals(shortArrayOf(5, 6, 7), decoded!!.samples)
        assertEquals(44_100, decoded.sampleRate)
    }

    @Test
    fun rejectsGarbageAndUnsupportedFormats() {
        assertNull(WavCodec.decode(ByteArray(0)))
        assertNull(WavCodec.decode(ByteArray(100) { 7 }))
        val stereo = WavCodec.encode(ShortArray(4), 48_000)
        stereo[22] = 2 // channels = 2
        assertNull(WavCodec.decode(stereo))
    }

    @Test
    fun truncatedDataIsReadAsFarAsAvailable() {
        val full = WavCodec.encode(shortArrayOf(1, 2, 3, 4), 48_000)
        val truncated = full.copyOfRange(0, full.size - 4) // drops the last two samples
        val decoded = WavCodec.decode(truncated)
        assertNotNull(decoded)
        assertArrayEquals(shortArrayOf(1, 2), decoded!!.samples)
    }
}
