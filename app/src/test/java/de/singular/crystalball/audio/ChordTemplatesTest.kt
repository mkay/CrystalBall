// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.audio

import de.singular.crystalball.chords.ChordLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line between what the app hears and what it draws.
 *
 * [Quality] carries both vocabularies, and the only thing keeping them apart is that
 * [ChordTemplates] enumerates [Quality.DETECTABLE] where [ChordLibrary] enumerates the whole enum.
 * That is one word in one loop, easy to "tidy" back into `entries` by someone who does not know
 * what it is for — at which point the recogniser silently starts scoring chords the chroma cannot
 * separate, and nothing else fails. These tests are what fails instead.
 */
class ChordTemplatesTest {

    @Test
    fun `only detectable qualities are given a template`() {
        val undetectable = ChordTemplates.CHORDS.filterNot { it.quality.detectable }
        assertTrue(
            "the recogniser is scoring chords it cannot hear: $undetectable",
            undetectable.isEmpty(),
        )
    }

    @Test
    fun `every detectable quality is scored, at every root`() {
        val expected = (0 until 12).flatMap { root -> Quality.DETECTABLE.map { Chord(root, it) } }
        assertEquals(expected.toSet(), ChordTemplates.CHORDS.toSet())
        assertEquals(expected.size, ChordTemplates.COUNT)
    }

    @Test
    fun `templates and priors stay aligned with the chords they belong to`() {
        // A label indexes all three. They are built from one list so they cannot drift, and this
        // says so out loud, because ChordRecognizer walks 0 until COUNT and indexes each in turn.
        assertEquals(ChordTemplates.COUNT, ChordTemplates.TEMPLATES.size)
        assertEquals(ChordTemplates.COUNT, ChordTemplates.QUALITY_BIAS.size)
    }

    @Test
    fun `the library draws every quality, heard or not`() {
        // The other half of the split: undetectable is a statement about the microphone, so it must
        // never thin out what can be looked up or written into a song.
        val drawn = ChordLibrary.allChords()
        assertEquals(12 * Quality.entries.size, drawn.size)
        for (quality in Quality.entries) {
            assertEquals(
                "the library draws ${quality.label} at fewer than twelve roots",
                12,
                drawn.count { it.quality == quality },
            )
        }
    }
}
