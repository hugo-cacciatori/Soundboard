package com.example.soundboard.audio.dsp.fx

/**
 * One offline audio effect. Pure Kotlin (no android.*). Effects run once per clip (never in the
 * mixer loop), so they may allocate and take a few hundred milliseconds.
 *
 * Input is mono float, about [-1, 1]. The result is a new array; its length may differ and its
 * peak may exceed 1 ([FxRenderer] re-normalizes afterwards).
 */
fun interface Effect {
    fun process(input: FloatArray, sampleRate: Int): FloatArray
}

/** Several effects run in order. To the rest of the app, a chain is just one effect. */
class EffectChain(private val effects: List<Effect>) : Effect {
    constructor(vararg effects: Effect) : this(effects.toList())

    override fun process(input: FloatArray, sampleRate: Int): FloatArray =
        effects.fold(input) { signal, effect -> effect.process(signal, sampleRate) }
}
