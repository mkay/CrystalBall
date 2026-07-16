package de.singular.crystalball.songs

import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.chords.Voicing
import org.junit.Assert.assertEquals
import org.junit.Test

/** Part names are unique within a song, and [upsertPart] is the only thing that promises it. */
class SongTest {

    private fun part(name: String, spec: String) = Part(
        name,
        listOf(SongChord(Chord(0, Quality.MAJ), Voicing.parse(spec, "open"))),
    )

    private val song = Song(id = "a", title = "T", capo = 0)

    @Test
    fun `a new part is appended`() {
        val next = song.upsertPart(part("Verse", "x32010")).upsertPart(part("Chorus", "320003"))
        assertEquals(listOf("Verse", "Chorus"), next.parts.map { it.name })
    }

    @Test
    fun `re-capturing a part replaces it in place`() {
        val next = song
            .upsertPart(part("Verse", "x32010"))
            .upsertPart(part("Chorus", "320003"))
            .upsertPart(part("Verse", "022000"))

        assertEquals(listOf("Verse", "Chorus"), next.parts.map { it.name })
        assertEquals("022000", next.parts.first().chords.single().voicing.spec)
    }

    @Test
    fun `removePart drops it`() {
        val next = song
            .upsertPart(part("Verse", "x32010"))
            .upsertPart(part("Chorus", "320003"))
            .removePart("Verse")
        assertEquals(listOf("Chorus"), next.parts.map { it.name })
    }

    @Test
    fun `removing an absent part is harmless`() {
        val next = song.upsertPart(part("Verse", "x32010")).removePart("Bridge")
        assertEquals(listOf("Verse"), next.parts.map { it.name })
    }
}
