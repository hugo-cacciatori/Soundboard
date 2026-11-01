package com.example.soundboard.audio

/** MP3 encoding through LAME (native, `src/main/cpp`). */
object Mp3Encoder {
    init {
        System.loadLibrary("soundboard")
    }

    /**
     * Encodes a mono 16-bit [clip] at [AudioConfig.SAMPLE_RATE] to the shared-clip MP3 format.
     * Blocking and CPU-bound: call it off the main thread. Null when LAME fails.
     */
    fun encode(clip: ShortArray): ByteArray? = nativeEncode(
        clip,
        AudioConfig.SAMPLE_RATE,
        AudioConfig.SHARE_MP3_SAMPLE_RATE,
        AudioConfig.SHARE_MP3_KBPS,
    )

    @JvmStatic
    private external fun nativeEncode(pcm: ShortArray, inRate: Int, outRate: Int, kbps: Int): ByteArray?
}
