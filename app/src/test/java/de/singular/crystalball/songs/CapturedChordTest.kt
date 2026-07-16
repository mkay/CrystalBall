package de.singular.crystalball.songs

import de.singular.crystalball.Capo
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.ChordCandidate
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.chords.ChordLibrary
import de.singular.crystalball.chords.Voicing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedChordTest {

    private val c = Chord(0, Quality.MAJ)
    private val am = Chord(9, Quality.MIN)
    private val g = Chord(7, Quality.MAJ)

    private fun captured(vararg chords: Chord) =
        CapturedChord(chords.mapIndexed { i, chord -> ChordCandidate(chord, 1f - i * 0.1f) })

    @Test
    fun `selected defaults to the recogniser's best fit`() {
        assertEquals(c, captured(c, am, g).selected)
    }

    @Test
    fun `alternatives exclude whatever is selected`() {
        val chord = captured(c, am, g)
        assertEquals(listOf(am, g), chord.alternatives.map { it.chord })
        assertEquals(listOf(c, g), chord.copy(selected = am).alternatives.map { it.chord })
    }

    @Test
    fun `an unchosen voicing resolves to the one the app would have shown anyway`() {
        val written = captured(c).toSongChord(capo = 0)
        assertEquals(c, written.sounding)
        assertEquals(ChordLibrary.voicingsFor(c, 0).first(), written.voicing)
    }

    @Test
    fun `a chosen voicing is what gets written down`() {
        val third = ChordLibrary.voicingsFor(c, 0)[2]
        val written = captured(c).copy(voicing = third).toSongChord(capo = 0)
        assertEquals(third, written.voicing)
        // Worth asserting: otherwise this passes even if the choice were being ignored.
        assertNotEquals(ChordLibrary.voicingsFor(c, 0).first(), third)
    }

    @Test
    fun `the default voicing follows the capo`() {
        // A capo makes you finger a different shape for the same sounding chord, and the stored
        // grip has to be the one your hands actually make.
        val shape = Capo.shapeChord(c, 2)
        assertEquals(ChordLibrary.voicingsFor(shape, 2).first(), defaultVoicing(c, 2))
        assertNotEquals(defaultVoicing(c, 0), defaultVoicing(c, 2))
    }

    @Test
    fun `every chord the recogniser can report has a default grip at every capo`() {
        // toSongChord() calls first() on the library's list; an empty one would crash a capture.
        for (capo in 0..Capo.MAX_FRET) {
            for (chord in ChordLibrary.allChords()) {
                assertTrue(
                    "no voicing for ${chord.name} at capo $capo",
                    defaultVoicing(chord, capo).frets.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `re-deriving a part at a new capo keeps the chords and moves the shapes`() {
        // What SongViewModel.setCapo does, and the reason sounding chords are what get stored: the
        // chords survive a capo move untouched because they are what the microphone heard, while
        // the grips are simply worked out again from the new fret.
        val part = Part("Verse", listOf(captured(c).toSongChord(0), captured(g).toSongChord(0)))
        val moved = part.copy(
            chords = part.chords.map { SongChord(it.sounding, defaultVoicing(it.sounding, 4)) },
        )
        assertEquals(part.chords.map { it.sounding }, moved.chords.map { it.sounding })
        assertNotEquals(part.chords.map { it.voicing }, moved.chords.map { it.voicing })
        assertEquals(defaultVoicing(c, 4), moved.chords.first().voicing)
    }

    @Test
    fun `a stored grip round-trips through the format`() {
        val written = captured(c).copy(voicing = Voicing.parse("x32010", "open")).toSongChord(0)
        val song = Song("id", "T", 0, listOf(Part("Verse", listOf(written))))
        assertEquals(song, SongJson.decode(SongJson.encode(listOf(song))).single())
    }
}
