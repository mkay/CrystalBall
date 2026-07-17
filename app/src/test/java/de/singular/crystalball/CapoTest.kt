package de.singular.crystalball

import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.chords.ChordLibrary
import de.singular.crystalball.chords.STANDARD_TUNING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapoTest {

    private fun chord(name: String): Chord = ChordLibrary.allChords().first { it.name == name }

    @Test
    fun `the canonical case - capo 2 and a D shape sounds E`() {
        assertEquals(chord("D"), Capo.shapeChord(chord("E"), capo = 2))
        assertEquals("E", Capo.title(chord("E"), 2, NameStyle.SOUNDING_FIRST))
        assertEquals("D shape · capo 2", Capo.subtitle(chord("E"), 2, NameStyle.SOUNDING_FIRST))
    }

    @Test
    fun `shape-first names it the other way round`() {
        assertEquals("D", Capo.title(chord("E"), 2, NameStyle.SHAPE_FIRST))
        assertEquals("sounds E · capo 2", Capo.subtitle(chord("E"), 2, NameStyle.SHAPE_FIRST))
    }

    @Test
    fun `no capo means no second name to show`() {
        assertEquals("E", Capo.title(chord("E"), 0, NameStyle.SOUNDING_FIRST))
        assertNull(Capo.subtitle(chord("E"), 0, NameStyle.SOUNDING_FIRST))
        assertNull(Capo.subtitle(chord("E"), 0, NameStyle.SHAPE_FIRST))
        assertNull(Capo.shapeLine(chord("E"), 0, NameStyle.SOUNDING_FIRST))
        assertNull(Capo.shapeLine(chord("E"), 0, NameStyle.SHAPE_FIRST))
        assertEquals(chord("E"), Capo.shapeChord(chord("E"), 0))
    }

    @Test
    fun `the shape line is the subtitle without the capo`() {
        // For the chord browser, which states the capo on its own line and would else say it twice.
        assertEquals("D shape", Capo.shapeLine(chord("E"), 2, NameStyle.SOUNDING_FIRST))
        assertEquals("sounds E", Capo.shapeLine(chord("E"), 2, NameStyle.SHAPE_FIRST))
    }

    @Test
    fun `transposition wraps around the octave`() {
        // C sounding with a capo at 2 is fingered as A#/Bb, not something below C.
        assertEquals(chord("A#"), Capo.shapeChord(chord("C"), 2))
        assertEquals(chord("G"), Capo.shapeChord(chord("C"), 5))
        assertEquals(chord("D#"), Capo.shapeChord(chord("C"), 9))
    }

    @Test
    fun `quality survives transposition`() {
        for (quality in Quality.entries) {
            val sounding = Chord(4, quality)
            assertEquals(quality, Capo.shapeChord(sounding, 3).quality)
        }
    }

    @Test
    fun `the shape fingered at the capo really does sound the detected chord`() {
        // The property the whole feature rests on: take the shape we would draw, add the capo to
        // every fret (open strings included — the capo is what stops them), and the notes that come
        // out must be the chord the microphone heard.
        for (capo in 1..Capo.MAX_FRET) {
            for (sounding in ChordLibrary.allChords()) {
                val shape = Capo.shapeChord(sounding, capo)
                val voicing = ChordLibrary.voicingsFor(shape).first()
                val sounded = buildSet {
                    for (s in voicing.frets.indices) {
                        val fret = voicing.frets[s]
                        if (fret >= 0) add((STANDARD_TUNING[s] + fret + capo) % 12)
                    }
                }
                val expected = sounding.tones.toSet()
                // A voicing may omit the fifth (e.g. C7 = x32310), so it must add no foreign note
                // and cover everything except possibly that.
                val fifth = (sounding.root + 7) % 12
                assertEquals(
                    "capo $capo, ${sounding.name} via ${shape.name} shape sounds wrong notes",
                    emptySet<Int>(), sounded - expected,
                )
                assertEquals(
                    "capo $capo, ${sounding.name} via ${shape.name} shape is missing notes",
                    emptySet<Int>(), (expected - fifth) - sounded,
                )
            }
        }
    }
}
