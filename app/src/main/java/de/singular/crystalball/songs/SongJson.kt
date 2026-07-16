package de.singular.crystalball.songs

import de.singular.crystalball.Capo
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.chords.Voicing
import org.json.JSONArray
import org.json.JSONObject

/**
 * The song library's on-disk format.
 *
 * Split from [SongRepository] because the format is the part that has to outlive every version of
 * the app, and this way it can be tested without a filesystem or an Android context.
 *
 * Nothing recogniser-shaped is stored. A capture's ranked candidates exist to fix a misread while
 * you still remember playing it, and their scores stop meaning anything the moment the recogniser
 * is retuned — so they die with the capture session, and a saved chord is just a chord.
 */
object SongJson {

    /** Bump only on a breaking change; [decode] refuses anything newer than it understands. */
    const val FORMAT = 1

    fun encode(songs: List<Song>): String =
        JSONObject().apply {
            put("format", FORMAT)
            put("songs", JSONArray().apply { songs.forEach { put(encodeSong(it)) } })
        }.toString()

    /**
     * Read the library back.
     *
     * Throws on a file that is unreadable or from a newer version, rather than returning an empty
     * library: the caller's next save would write that emptiness over a file we merely failed to
     * understand. Individual entries that are malformed *are* dropped — one bad chord is not worth
     * losing the library over, and there is nothing to recover from it anyway.
     */
    fun decode(text: String): List<Song> {
        val root = JSONObject(text)
        val format = root.optInt("format", FORMAT)
        check(format <= FORMAT) { "song library format $format is newer than $FORMAT" }
        val songs = root.optJSONArray("songs") ?: return emptyList()
        return (0 until songs.length()).mapNotNull { i ->
            songs.optJSONObject(i)?.let(::decodeSong)
        }
    }

    private fun encodeSong(song: Song) = JSONObject().apply {
        put("id", song.id)
        put("title", song.title)
        put("capo", song.capo)
        put("comment", song.comment)
        put("updatedAt", song.updatedAt)
        put("parts", JSONArray().apply { song.parts.forEach { put(encodePart(it)) } })
    }

    private fun encodePart(part: Part) = JSONObject().apply {
        put("name", part.name)
        put("chords", JSONArray().apply { part.chords.forEach { put(encodeChord(it)) } })
    }

    private fun encodeChord(chord: SongChord) = JSONObject().apply {
        put("root", chord.sounding.root)
        // By name, not ordinal: Quality's declaration order is load-bearing for the templates and
        // the enum is documented as append-only, but a file outlives that promise.
        put("quality", chord.sounding.quality.name)
        put("frets", chord.voicing.spec)
        put("label", chord.voicing.label)
    }

    private fun decodeSong(o: JSONObject): Song? {
        val id = o.optString("id")
        if (id.isEmpty()) return null
        val parts = o.optJSONArray("parts") ?: JSONArray()
        return Song(
            id = id,
            title = o.optString("title"),
            capo = o.optInt("capo").coerceIn(0, Capo.MAX_FRET),
            // Absent in songs written before comments existed, and "" is exactly right for them.
            comment = o.optString("comment"),
            // Repairs a file that breaks the uniqueness rule; the model promises it holds.
            parts = (0 until parts.length())
                .mapNotNull { parts.optJSONObject(it)?.let(::decodePart) }
                .distinctBy { it.name },
            updatedAt = o.optLong("updatedAt"),
        )
    }

    private fun decodePart(o: JSONObject): Part? {
        val name = o.optString("name")
        if (name.isEmpty()) return null
        val chords = o.optJSONArray("chords") ?: JSONArray()
        return Part(
            name = name,
            chords = (0 until chords.length()).mapNotNull { chords.optJSONObject(it)?.let(::decodeChord) },
        )
    }

    private fun decodeChord(o: JSONObject): SongChord? {
        val root = o.optInt("root", -1)
        if (root !in 0..11) return null
        val quality = Quality.entries.firstOrNull { it.name == o.optString("quality") } ?: return null
        // parse rejects a bad spec, and Voicing itself rejects a wrong string count.
        val voicing = runCatching {
            Voicing.parse(o.optString("frets"), o.optString("label"))
        }.getOrNull() ?: return null
        return SongChord(Chord(root, quality), voicing)
    }
}
