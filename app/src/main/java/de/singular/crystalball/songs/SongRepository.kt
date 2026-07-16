package de.singular.crystalball.songs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The song library: one JSON file under `filesDir`.
 *
 * A plain file, like Rubber Ring's library index and for the same reason — the data is one short
 * list, and Room or DataStore would be machinery maintained for its own sake. Unlike that one there
 * are no blobs to carry, so this file *is* the library: exporting it is copying it out.
 *
 * Reads and writes are serialised with a [Mutex] and run on [Dispatchers.IO], so callers can use it
 * straight from a view-model scope without thinking about it.
 *
 * Takes a [File] rather than a [Context] so the round-trip is testable on the JVM; the [Context]
 * constructor is what the app actually calls.
 */
class SongRepository(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val mutex = Mutex()

    /** Every song, in the order the file holds them. Sorting is the library screen's business. */
    suspend fun list(): List<Song> = withContext(Dispatchers.IO) { mutex.withLock { read() } }

    /**
     * Add [song], or replace the one with its id, keeping its place in the list. Returns what was
     * written, which is [song] with its [Song.updatedAt] stamped.
     */
    suspend fun save(song: Song): Song = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stamped = song.copy(updatedAt = System.currentTimeMillis())
            val songs = read()
            val index = songs.indexOfFirst { it.id == song.id }
            write(
                if (index < 0) songs + stamped
                else songs.toMutableList().also { it[index] = stamped },
            )
            stamped
        }
    }

    /** Drop the song with [id], if it is there. */
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { write(read().filterNot { it.id == id }) }
    }

    private fun read(): List<Song> =
        if (file.exists()) SongJson.decode(file.readText()) else emptyList()

    /**
     * Write via a temporary file and rename over the real one.
     *
     * The rename is atomic within a directory, so a crash or a battery death mid-write leaves the
     * previous library intact rather than a half-written file that [SongJson.decode] would refuse —
     * which, for a single-file library, is every song the user has.
     */
    private fun write(songs: List<Song>) {
        val parent = file.parentFile
        parent?.mkdirs()
        val tmp = File(parent, "${file.name}.tmp")
        tmp.writeText(SongJson.encode(songs))
        check(tmp.renameTo(file)) { "could not replace ${file.name}" }
    }

    private companion object {
        const val FILE_NAME = "songs.json"
    }
}
