package de.singular.crystalball.songs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The song library: one JSON file under `filesDir`.
 *
 * A plain file, like Rubber Ring's library index and for the same reason — the data is one short
 * list, and Room or DataStore would be machinery maintained for its own sake. Unlike that one there
 * are no blobs to carry, so this file *is* the library: a backup is this file in a zip, and a
 * restore is it back out again.
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

    // ---- Backup / restore ----
    //
    // A backup is a plain (unencrypted) zip: a manifest and the song library. Rubber Ring's shape,
    // so a backup from either app is the same kind of thing to a user holding both — its zip is a
    // container for audio, and this one carries a single JSON file it does not need a container for,
    // but the consistency is worth more than the two bytes.
    //
    // Songs only. Settings are not in here and should not be: the library is what a player cannot
    // reconstruct, while a theme is five taps. Keeping them out means adding a setting stays a
    // one-line change instead of a question about the backup format.

    /**
     * Write every song to [output] as a backup zip.
     *
     * Re-encodes rather than copying the file byte-for-byte, so an export is always a well-formed
     * backup — and a library too broken to read fails here rather than producing a backup that
     * cannot be restored.
     */
    suspend fun exportTo(output: OutputStream) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ZipOutputStream(output.buffered()).use { zip ->
                zip.writeText(MANIFEST_ENTRY, manifestJson())
                zip.writeText(SONGS_ENTRY, SongJson.encode(read()))
            }
        }
    }

    /**
     * Replace the whole library with the songs in the backup zip read from [input], and return how
     * many arrived.
     *
     * The zip is read whole and decoded before anything is written, so a truncated or corrupt
     * backup leaves the library untouched. That is all the staging this needs — Rubber Ring copies
     * to a temp directory because it is swapping in a folder of audio, whereas one decoded list of
     * songs and the atomic [write] below cannot land half-applied.
     */
    suspend fun importFrom(input: InputStream): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            var songsJson: String? = null
            var sawManifest = false

            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST_ENTRY -> {
                            val format = JSONObject(zip.readBytes().decodeToString())
                                .optInt("format", 1)
                            if (format > BACKUP_FORMAT) {
                                throw IOException("This backup is from a newer version of Crystal Ball.")
                            }
                            sawManifest = true
                        }

                        SONGS_ENTRY -> songsJson = zip.readBytes().decodeToString()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            if (!sawManifest) throw IOException("This file isn't a Crystal Ball backup.")
            val json = songsJson ?: throw IOException("This backup has no songs in it.")
            // Decode before writing: SongJson refuses a file it cannot read, and that refusal has to
            // land before the library is replaced rather than after.
            val songs = SongJson.decode(json)
            write(songs)
            songs.size
        }
    }

    /**
     * The zip's own version, which is not [SongJson.FORMAT].
     *
     * That one versions how a song is written; this one versions the layout around it — which
     * entries exist and what they mean. Adding an entry could bump this while the song format stands
     * still, and changing how a chord is stored bumps that one while the layout stands still.
     */
    private fun manifestJson(): String = JSONObject()
        .put("app", APP_NAME)
        .put("format", BACKUP_FORMAT)
        .put("createdAt", System.currentTimeMillis())
        .toString()

    private fun ZipOutputStream.writeText(entryName: String, text: String) {
        putNextEntry(ZipEntry(entryName))
        write(text.toByteArray())
        closeEntry()
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

        // The backup zip's layout. Bump BACKUP_FORMAT only on a breaking change; importFrom refuses
        // a backup claiming a format newer than it understands, rather than reading it wrong.
        const val BACKUP_FORMAT = 1
        const val APP_NAME = "CrystalBall"
        const val MANIFEST_ENTRY = "manifest.json"
        const val SONGS_ENTRY = "songs.json"
    }
}
