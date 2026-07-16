package de.singular.crystalball.songs

import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.chords.Voicing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SongRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repo(): Pair<SongRepository, File> {
        val file = File(folder.root, "songs.json")
        return SongRepository(file) to file
    }

    private fun song(id: String, title: String = id) = Song(
        id = id,
        title = title,
        capo = 0,
        parts = listOf(
            Part("Verse", listOf(SongChord(Chord(0, Quality.MAJ), Voicing.parse("x32010", "open")))),
        ),
    )

    @Test
    fun `an absent file is an empty library, not a crash`() = runBlocking {
        val (repo, _) = repo()
        assertEquals(emptyList<Song>(), repo.list())
    }

    @Test
    fun `a saved song comes back`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))
        assertEquals(listOf("a"), repo.list().map { it.id })
        assertEquals(song("a").parts, repo.list().single().parts)
    }

    @Test
    fun `saving stamps updatedAt`() = runBlocking {
        val (repo, _) = repo()
        val before = System.currentTimeMillis()
        val saved = repo.save(song("a"))
        assertTrue(saved.updatedAt >= before)
        assertEquals(saved.updatedAt, repo.list().single().updatedAt)
    }

    @Test
    fun `saving the same id replaces in place rather than appending`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))
        repo.save(song("b"))
        repo.save(song("a", title = "renamed"))
        assertEquals(listOf("a", "b"), repo.list().map { it.id })
        assertEquals("renamed", repo.list().first().title)
    }

    @Test
    fun `remove drops only its own song`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))
        repo.save(song("b"))
        repo.remove("a")
        assertEquals(listOf("b"), repo.list().map { it.id })
    }

    @Test
    fun `removing something absent is harmless`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))
        repo.remove("nope")
        assertEquals(listOf("a"), repo.list().map { it.id })
    }

    @Test
    fun `a second repository over the same file sees the songs`() = runBlocking {
        val (repo, file) = repo()
        repo.save(song("a"))
        assertEquals(listOf("a"), SongRepository(file).list().map { it.id })
    }

    @Test
    fun `writing leaves no temp file behind`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))
        assertEquals(listOf("songs.json"), folder.root.list()!!.sorted())
    }
}
