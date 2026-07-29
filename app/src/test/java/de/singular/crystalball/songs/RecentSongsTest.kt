// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.songs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Recent" counts both reading a song and correcting one, whichever happened later. */
class RecentSongsTest {

    private fun song(title: String, updated: Long = 0L, opened: Long = 0L) =
        Song(id = title, title = title, capo = 0, updatedAt = updated, lastOpenedAt = opened)

    private fun titles(songs: List<Song>) = songs.map { it.title }

    @Test
    fun `the most recently edited comes first`() {
        val songs = listOf(song("old", updated = 100), song("new", updated = 300))
        assertEquals(listOf("new", "old"), titles(songs.recent()))
    }

    @Test
    fun `opening a song floats it above one edited earlier`() {
        val songs = listOf(song("edited", updated = 200), song("read", opened = 300))
        assertEquals(listOf("read", "edited"), titles(songs.recent()))
    }

    @Test
    fun `editing a song floats it above one opened earlier`() {
        val songs = listOf(song("read", opened = 200), song("edited", updated = 300))
        assertEquals(listOf("edited", "read"), titles(songs.recent()))
    }

    /** The whole reason for maxOf: a song can carry both stamps, and only the later one matters. */
    @Test
    fun `a song is ranked by its later stamp, not by either alone`() {
        val songs = listOf(
            song("both", updated = 100, opened = 400),
            song("editedLater", updated = 300),
        )
        assertEquals(listOf("both", "editedLater"), titles(songs.recent()))
    }

    @Test
    fun `a song neither opened nor saved is left out`() {
        val songs = listOf(song("untouched"), song("read", opened = 100))
        assertEquals(listOf("read"), titles(songs.recent()))
    }

    @Test
    fun `an untouched library offers nothing rather than an arbitrary list`() {
        assertTrue(listOf(song("a"), song("b")).recent().isEmpty())
    }

    @Test
    fun `no more than the limit are offered`() {
        val songs = (1..10).map { song("s$it", opened = it.toLong()) }
        assertEquals(RECENTS_LIMIT, songs.recent().size)
        assertEquals("s10", songs.recent().first().title)
    }

    @Test
    fun `the limit can be asked for explicitly`() {
        val songs = (1..10).map { song("s$it", opened = it.toLong()) }
        assertEquals(listOf("s10", "s9"), titles(songs.recent(limit = 2)))
    }
}
