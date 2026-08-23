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
    fun `everything the app can hear, it can also draw`() {
        // The other half of the split, and the direction that must never break: being undetectable
        // may keep a chord out of the recogniser, but nothing may keep a *detected* chord out of the
        // library — the result screen has a diagram to fill either way.
        for (quality in Quality.DETECTABLE) {
            assertTrue(
                "${quality.label} can be heard but not drawn",
                quality in ChordLibrary.DRAWABLE,
            )
        }
    }

    @Test
    fun `a drawable quality is drawable at every root`() {
        val drawn = ChordLibrary.allChords()
        assertEquals(12 * ChordLibrary.DRAWABLE.size, drawn.size)
        for (quality in ChordLibrary.DRAWABLE) {
            assertEquals(
                "the library draws ${quality.label} at fewer than twelve roots",
                12,
                drawn.count { it.quality == quality },
            )
        }
    }

    @Test
    fun `the library never offers a chord it cannot fret`() {
        // What DRAWABLE is for. voicingsFor promises never to return empty and callers take its
        // first entry without checking, so a quality that reached the chooser before its shapes did
        // would not degrade — it would throw, on a chord the user had just tapped.
        for (chord in ChordLibrary.allChords()) {
            assertTrue(
                "${chord.name} is offered with no shape behind it",
                ChordLibrary.voicingsFor(chord).isNotEmpty(),
            )
        }
    }
}
