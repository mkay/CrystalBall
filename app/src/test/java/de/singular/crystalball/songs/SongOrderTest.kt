package de.singular.crystalball.songs

import org.junit.Assert.assertEquals
import org.junit.Test

/** Parts are captured in the order you played them, which is rarely the order they belong in. */
class SongOrderTest {

    private fun song(vararg names: String) =
        Song("a", "T", 0, names.map { Part(it, emptyList()) })

    private fun names(song: Song) = song.parts.map { it.name }

    @Test
    fun `a part moves up`() {
        assertEquals(
            listOf("Chorus", "Verse", "Bridge"),
            names(song("Verse", "Chorus", "Bridge").movePart(1, -1)),
        )
    }

    @Test
    fun `a part moves down`() {
        assertEquals(
            listOf("Chorus", "Verse", "Bridge"),
            names(song("Verse", "Chorus", "Bridge").movePart(0, 1)),
        )
    }

    @Test
    fun `moving off the top does nothing rather than wrapping`() {
        val start = song("Verse", "Chorus")
        assertEquals(start, start.movePart(0, -1))
    }

    @Test
    fun `moving off the bottom does nothing rather than wrapping`() {
        val start = song("Verse", "Chorus")
        assertEquals(start, start.movePart(1, 1))
    }

    @Test
    fun `an index that is not there is harmless`() {
        val start = song("Verse")
        assertEquals(start, start.movePart(5, 1))
    }

    @Test
    fun `moving keeps every part exactly once`() {
        val moved = song("Intro", "Verse", "Chorus", "Outro").movePart(3, -1)
        assertEquals(listOf("Intro", "Verse", "Outro", "Chorus"), names(moved))
    }
}
