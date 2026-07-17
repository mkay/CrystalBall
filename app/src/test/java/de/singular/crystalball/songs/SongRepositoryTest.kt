package de.singular.crystalball.songs

import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.chords.Voicing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
    fun `removeAll drops the whole selection and keeps the rest`() = runBlocking {
        val (repo, _) = repo()
        listOf("a", "b", "c", "d").forEach { repo.save(song(it)) }
        repo.removeAll(setOf("a", "c", "nope"))
        assertEquals(listOf("b", "d"), repo.list().map { it.id })
    }

    @Test
    fun `removeAll of nothing leaves the library alone`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))
        repo.removeAll(emptySet())
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

    // ---- Backup / restore ----

    /** Back [repo] up and hand back the bytes, as the content resolver's stream would. */
    private suspend fun backupOf(repo: SongRepository): ByteArray =
        ByteArrayOutputStream().also { repo.exportTo(it) }.toByteArray()

    private fun otherRepo(name: String) = SongRepository(File(folder.root, name))

    @Test
    fun `a backup is a zip of a manifest and the songs, like Rubber Ring's`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))

        val entries = mutableMapOf<String, String>()
        ZipInputStream(backupOf(repo).inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries[e.name] = zip.readBytes().decodeToString()
                zip.closeEntry()
                e = zip.nextEntry
            }
        }

        assertEquals(setOf("manifest.json", "songs.json"), entries.keys)
        val manifest = JSONObject(entries.getValue("manifest.json"))
        assertEquals("CrystalBall", manifest.getString("app"))
        assertEquals(1, manifest.getInt("format"))
        assertTrue(manifest.getLong("createdAt") > 0)
        // Settings are deliberately absent: adding one must not be a backup-format question.
        assertTrue("settings.json" !in entries)
        // The songs entry is the library's own format, readable on its own.
        assertEquals(listOf("a"), SongJson.decode(entries.getValue("songs.json")).map { it.id })
    }

    @Test
    fun `a backup restores into a different library`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a", title = "Rubber Ring"))
        repo.save(song("b"))

        val restored = otherRepo("other.json")
        assertEquals(2, restored.importFrom(backupOf(repo).inputStream()))
        assertEquals(listOf("a", "b"), restored.list().map { it.id })
        assertEquals("Rubber Ring", restored.list().first().title)
        // The parts are the point of a song, so prove they survive rather than just the titles.
        assertEquals(song("a").parts, restored.list().first().parts)
    }

    @Test
    fun `restoring replaces the library rather than merging into it`() = runBlocking {
        val (source, _) = repo()
        source.save(song("a"))
        val backup = backupOf(source)

        val target = otherRepo("target.json")
        target.save(song("gone"))
        target.importFrom(backup.inputStream())
        assertEquals(listOf("a"), target.list().map { it.id })
    }

    @Test
    fun `an empty library backs up and restores as empty`() = runBlocking {
        val (repo, _) = repo()
        val target = otherRepo("target.json")
        target.save(song("gone"))
        assertEquals(0, target.importFrom(backupOf(repo).inputStream()))
        assertEquals(emptyList<Song>(), target.list())
    }

    @Test
    fun `a file that is not a backup is refused`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))
        // A zip, but somebody else's.
        val stranger = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("hello.txt"))
                zip.write("not a backup".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        assertThrows(IOException::class.java) {
            runBlocking { repo.importFrom(stranger.inputStream()) }
        }
        assertEquals(listOf("a"), repo.list().map { it.id })
    }

    @Test
    fun `a backup from a newer app is refused rather than read wrong`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))
        val fromTheFuture = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("""{"app":"CrystalBall","format":99}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("songs.json"))
                zip.write("""{"format":1,"songs":[]}""".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        assertThrows(IOException::class.java) {
            runBlocking { repo.importFrom(fromTheFuture.inputStream()) }
        }
        assertEquals(listOf("a"), repo.list().map { it.id })
    }

    @Test
    fun `a truncated backup leaves the library untouched`() = runBlocking {
        val (source, _) = repo()
        source.save(song("a"))
        val half = backupOf(source).let { it.copyOf(it.size / 2) }

        val target = otherRepo("target.json")
        target.save(song("keep"))
        runCatching { target.importFrom(half.inputStream()) }
        assertEquals(listOf("keep"), target.list().map { it.id })
    }

    @Test
    fun `restoring leaves no temp file behind`() = runBlocking {
        val (repo, _) = repo()
        repo.save(song("a"))
        repo.importFrom(backupOf(repo).inputStream())
        assertEquals(listOf("songs.json"), folder.root.list()!!.sorted())
    }
}
