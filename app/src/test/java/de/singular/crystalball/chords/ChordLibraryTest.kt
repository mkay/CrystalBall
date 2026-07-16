package de.singular.crystalball.chords

import de.singular.crystalball.Capo
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Correctness of the shape tables. The curated grips and movable forms are hand-authored, so these
 * tests exist to prove each one actually sounds the chord it claims to — a transposed digit is
 * invisible on inspection but caught here.
 */
class ChordLibraryTest {

    /** The perfect fifth is the one tone a guitar voicing may freely omit (e.g. C7 = x32310). */
    private fun requiredTones(chord: Chord): Set<Int> {
        val fifth = (chord.root + 7) % 12
        return chord.tones.toSet() - fifth
    }

    @Test
    fun `every voicing sounds only notes belonging to its chord`() {
        for (chord in ChordLibrary.allChords()) {
            for (v in ChordLibrary.voicingsFor(chord)) {
                val sounded = v.soundedPitchClasses()
                val foreign = sounded - chord.tones.toSet()
                assertTrue(
                    "${chord.name} ${v.frets.toList()} (${v.label}) sounds foreign notes $foreign",
                    foreign.isEmpty(),
                )
            }
        }
    }

    @Test
    fun `every voicing sounds the root, the third or suspension, and any seventh`() {
        for (chord in ChordLibrary.allChords()) {
            for (v in ChordLibrary.voicingsFor(chord)) {
                val missing = requiredTones(chord) - v.soundedPitchClasses()
                assertTrue(
                    "${chord.name} ${v.frets.toList()} (${v.label}) is missing $missing",
                    missing.isEmpty(),
                )
            }
        }
    }

    @Test
    fun `every chord has enough voicings to be worth showing`() {
        // Three is the real floor, and it is not a gap to be filled: a m7 or sus chord genuinely
        // has only the three barre forms below the 15th fret. Anything beyond them would be a
        // fingering no guitarist reaches for, so the UI shows what exists rather than padding.
        for (chord in ChordLibrary.allChords()) {
            val count = ChordLibrary.voicingsFor(chord).size
            assertTrue("${chord.name} has only $count voicings", count >= 3)
        }
    }

    @Test
    fun `triads nearly fill the variations row`() {
        // Majors and minors are the common case and carry the CAGED forms plus the three-string
        // triads. Five is the floor rather than six: a few minors (Cm, F#m, Gm…) sit high enough
        // that the octave-up repeat of a form runs off the end of the neck.
        for (chord in ChordLibrary.allChords()) {
            if (chord.quality != Quality.MAJ && chord.quality != Quality.MIN) continue
            val count = ChordLibrary.voicingsFor(chord).size
            assertTrue("${chord.name} has only $count voicings", count >= 5)
        }
    }

    @Test
    fun `curated open shapes are offered first`() {
        val c = ChordLibrary.voicingsFor(Chord(0, Quality.MAJ)).first()
        assertEquals(listOf(MUTED, 3, 2, 0, 1, 0), c.frets.toList())
        assertEquals("open", c.label)

        val am = ChordLibrary.voicingsFor(Chord(9, Quality.MIN)).first()
        assertEquals(listOf(MUTED, 0, 2, 2, 1, 0), am.frets.toList())
    }

    @Test
    fun `chords with no open shape still generate movable forms`() {
        // F# minor has no open grip at all; it must still be drawable.
        val voicings = ChordLibrary.voicingsFor(Chord(6, Quality.MIN))
        assertTrue(voicings.isNotEmpty())
        assertTrue(voicings.all { it.frets.any { f -> f > 0 } })
    }

    @Test
    fun `no voicing reaches past the end of a typical neck`() {
        for (chord in ChordLibrary.allChords()) {
            for (v in ChordLibrary.voicingsFor(chord)) {
                assertTrue("${chord.name} ${v.frets.toList()} is too high", v.highestFret <= 15)
            }
        }
    }

    @Test
    fun `voicings are ordered from the nut upwards`() {
        for (chord in ChordLibrary.allChords()) {
            val generated = ChordLibrary.voicingsFor(chord).filter { it.label != "open" }
            val positions = generated.map { it.lowestFret }
            assertEquals("${chord.name} generated shapes are out of order", positions.sorted(), positions)
        }
    }

    @Test
    fun `the bass note of a curated open chord is its root`() {
        // The recogniser leans on root-in-the-bass; the shapes we show should agree with that.
        for (name in listOf("C", "A", "G", "E", "D", "Am", "Em", "Dm")) {
            val chord = ChordLibrary.allChords().first { it.name == name }
            val open = ChordLibrary.voicingsFor(chord).first()
            assertNotNull(open.bassPitchClass())
            assertEquals("$name open shape has the wrong bass", chord.root, open.bassPitchClass())
        }
    }

    @Test
    fun `a capo drops the shapes it pushes off the end of the neck`() {
        // Frets are counted from the capo, so a capo at 2 puts a 14th-fret shape at the 16th.
        for (chord in ChordLibrary.allChords()) {
            for (capo in 0..Capo.MAX_FRET) {
                for (v in ChordLibrary.voicingsFor(chord, capo)) {
                    assertTrue(
                        "${chord.name} ${v.frets.toList()} sits at fret ${v.highestFret + capo} with capo $capo",
                        v.highestFret + capo <= 15,
                    )
                }
            }
        }
    }

    @Test
    fun `every chord keeps a playable shape at every capo position`() {
        // The UI takes the first voicing unconditionally, so an empty list is a crash. Filtering by
        // capo must never remove every option.
        for (chord in ChordLibrary.allChords()) {
            for (capo in 0..Capo.MAX_FRET) {
                assertTrue(
                    "${chord.name} has nothing playable at capo $capo",
                    ChordLibrary.voicingsFor(chord, capo).isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `a capo only ever removes shapes, never invents them`() {
        for (chord in ChordLibrary.allChords()) {
            val open = ChordLibrary.voicingsFor(chord, capo = 0)
            for (capo in 1..Capo.MAX_FRET) {
                val capoed = ChordLibrary.voicingsFor(chord, capo)
                assertTrue("${chord.name} gained shapes at capo $capo", open.containsAll(capoed))
            }
        }
    }

    @Test
    fun `position labels count frets from the guitar's nut, not the capo`() {
        // A shape held at its own 5th fret is at the guitar's 7th when the capo is at 2 — the label
        // must say what the player reads off their own neck.
        val chord = ChordLibrary.allChords().first { it.name == "D" }
        val plain = ChordLibrary.voicingsFor(chord, capo = 0).first { it.label.contains("fret") }
        val capoed = ChordLibrary.voicingsFor(chord, capo = 2).first { it.label.contains("fret") }
        assertEquals(plain.frets.toList(), capoed.frets.toList()) // same grip…
        assertEquals("2nd fret · C form", plain.label)
        assertEquals("4th fret · C form", capoed.label) // …named two frets higher
    }

    @Test
    fun `every fretted shape is named two frets higher with a capo at two`() {
        // The property behind the case above, across the whole vocabulary: the grip is unchanged,
        // only the fret it is named after moves, and it moves by exactly the capo.
        for (chord in ChordLibrary.allChords()) {
            val plain = ChordLibrary.voicingsFor(chord, capo = 0).associateBy { it.frets.toList() }
            for (v in ChordLibrary.voicingsFor(chord, capo = 2)) {
                val before = plain.getValue(v.frets.toList())
                val expected = if (v.lowestFret == 0) before.label
                else before.label.replace(Regex("^\\S+ fret"), "${v.lowestFret + 2}${suffix(v.lowestFret + 2)} fret")
                assertEquals("${chord.name} ${v.frets.toList()}", expected, v.label)
            }
        }
    }

    private fun suffix(n: Int): String = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    @Test
    fun `open-position shapes stay named open behind a capo`() {
        val chord = ChordLibrary.allChords().first { it.name == "D" }
        assertEquals("open", ChordLibrary.voicingsFor(chord, capo = 4).first().label)
    }

    @Test
    fun `parse reads muted strings and two-digit frets`() {
        assertEquals(listOf(MUTED, 3, 2, 0, 1, 0), Voicing.parse("x32010", "open").frets.toList())
        assertEquals(listOf(10, 12, 12, 11, 10, 10), Voicing.parse("10-12-12-11-10-10", "x").frets.toList())
    }
}
