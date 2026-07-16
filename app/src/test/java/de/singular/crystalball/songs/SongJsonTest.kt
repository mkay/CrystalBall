package de.singular.crystalball.songs

import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.chords.Voicing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongJsonTest {

    private fun chord(root: Int, quality: Quality, spec: String, label: String = "open") =
        SongChord(Chord(root, quality), Voicing.parse(spec, label))

    private val song = Song(
        id = "abc",
        title = "Wonderwall",
        capo = 2,
        comment = "Drop D. Second verse drops to picking.",
        parts = listOf(
            Part("Verse", listOf(chord(4, Quality.MIN7, "020000"), chord(7, Quality.MAJ, "320003"))),
            Part("Chorus", listOf(chord(0, Quality.MAJ, "x32010"))),
        ),
        updatedAt = 1_700_000_000_000,
    )

    @Test
    fun `a song round-trips whole`() {
        assertEquals(listOf(song), SongJson.decode(SongJson.encode(listOf(song))))
    }

    @Test
    fun `an empty library round-trips`() {
        assertEquals(emptyList<Song>(), SongJson.decode(SongJson.encode(emptyList())))
    }

    @Test
    fun `quality survives being reordered in the enum`() {
        // Stored by name, so the file does not depend on Quality's declaration order — which is
        // load-bearing for the templates and could grow.
        assertTrue(SongJson.encode(listOf(song)).contains("\"quality\":\"MIN7\""))
    }

    @Test(expected = IllegalStateException::class)
    fun `a newer format is refused rather than read as empty`() {
        // Reading it as empty would let the next save overwrite a library we merely can't parse.
        SongJson.decode("""{"format":${SongJson.FORMAT + 1},"songs":[]}""")
    }

    @Test
    fun `a malformed chord is dropped, not the library`() {
        val json = """
            {"format":1,"songs":[{"id":"a","title":"T","capo":0,"parts":[
              {"name":"Verse","chords":[
                {"root":0,"quality":"MAJ","frets":"x32010","label":"open"},
                {"root":99,"quality":"MAJ","frets":"x32010","label":"open"},
                {"root":0,"quality":"NOPE","frets":"x32010","label":"open"},
                {"root":0,"quality":"MAJ","frets":"nonsense","label":"open"},
                {"root":0,"quality":"MAJ","frets":"x3201","label":"open"}
              ]}
            ]}]}
        """.trimIndent()
        val parts = SongJson.decode(json).single().parts
        assertEquals(1, parts.single().chords.size)
    }

    @Test
    fun `a song without an id is dropped`() {
        val json = """{"format":1,"songs":[{"title":"no id"},{"id":"b","title":"kept"}]}"""
        assertEquals(listOf("kept"), SongJson.decode(json).map { it.title })
    }

    @Test
    fun `duplicate part names are repaired on read`() {
        val json = """
            {"format":1,"songs":[{"id":"a","title":"T","capo":0,"parts":[
              {"name":"Verse","chords":[]},
              {"name":"Verse","chords":[]}
            ]}]}
        """.trimIndent()
        assertEquals(1, SongJson.decode(json).single().parts.size)
    }

    @Test
    fun `a song written before comments existed reads back with an empty one`() {
        // The field arrived after the first songs did; absent has to mean "no comment", not a
        // dropped song.
        val json = """{"format":1,"songs":[{"id":"a","title":"T","capo":0,"parts":[]}]}"""
        assertEquals("", SongJson.decode(json).single().comment)
    }

    @Test
    fun `an out-of-range capo is clamped`() {
        val json = """{"format":1,"songs":[{"id":"a","title":"T","capo":99,"parts":[]}]}"""
        assertEquals(7, SongJson.decode(json).single().capo)
    }
}
