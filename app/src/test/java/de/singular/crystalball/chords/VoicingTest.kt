package de.singular.crystalball.chords

import org.junit.Assert.assertEquals
import org.junit.Test

/** [Voicing.spec] is what a saved song stores, so it has to survive a round trip exactly. */
class VoicingTest {

    @Test
    fun `spec round-trips an open shape`() {
        val c = Voicing.parse("x32010", "open")
        assertEquals("x32010", c.spec)
        assertEquals(c, Voicing.parse(c.spec, c.label))
    }

    @Test
    fun `spec round-trips a shape with two-digit frets`() {
        val up = Voicing.parse("10-12-12-11-10-10", "10th fret")
        assertEquals("10-12-12-11-10-10", up.spec)
        assertEquals(up, Voicing.parse(up.spec, up.label))
    }

    @Test
    fun `spec separates when a muted string sits beside two-digit frets`() {
        val shape = Voicing.parse("x-10-12-12-12-10", "10th fret")
        assertEquals("x-10-12-12-12-10", shape.spec)
        assertEquals(shape, Voicing.parse(shape.spec, shape.label))
    }

    @Test
    fun `every library voicing round-trips`() {
        // The real corpus, rather than shapes chosen to pass: curated and generated, capo and not.
        for (capo in 0..2) {
            for (chord in ChordLibrary.allChords()) {
                for (voicing in ChordLibrary.voicingsFor(chord, capo)) {
                    assertEquals(
                        "round trip failed for ${chord.name} ${voicing.spec}",
                        voicing,
                        Voicing.parse(voicing.spec, voicing.label),
                    )
                }
            }
        }
    }
}
