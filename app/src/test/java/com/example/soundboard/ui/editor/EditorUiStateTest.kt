package com.example.soundboard.ui.editor

import com.example.soundboard.audio.dsp.fx.Fx
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUiStateTest {
    private val ready = EditorUiState(name = "Hey", hasSound = true)

    @Test
    fun otherEffectsSaveWithoutAMelody() {
        assertTrue(ready.canSave)
        assertTrue(ready.copy(fxId = Fx.Robot.id).canSave)
        assertFalse(ready.copy(fxId = Fx.Robot.id).melodyMissing)
    }

    @Test
    fun melodyEffectNeedsAMelody() {
        val melody = ready.copy(fxId = Fx.Melody.id)
        assertTrue(melody.melodyMissing)
        assertFalse(melody.canSave)
    }

    @Test
    fun melodyEffectNeedsAtLeastOneNote() {
        val empty = ready.copy(fxId = Fx.Melody.id, melodyId = "m1", melodyHasNotes = false)
        assertFalse(empty.canSave)
        assertTrue(empty.copy(melodyHasNotes = true).canSave)
    }
}
