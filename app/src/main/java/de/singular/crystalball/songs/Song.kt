package de.singular.crystalball.songs

import de.singular.crystalball.audio.Chord
import de.singular.crystalball.chords.Voicing

/**
 * The part names offered as chips, so naming one is a tap rather than typing with a guitar on your
 * lap. Not a closed set — a part can be called anything — just the ones worth not spelling out.
 */
val PART_NAMES = listOf("Intro", "Verse", "Chorus", "Bridge", "Solo", "Outro")

/**
 * One chord in a part: the chord it sounds, and the grip chosen to play it.
 *
 * [sounding] is always what the microphone heard, never a shape — the shape is derived from it and
 * the song's capo, the way the rest of the app does it. [voicing] is the whole point of recording a
 * song rather than a chord name: it is *how you play this*, picked from the alternatives the result
 * page already offers, and defaulting to the one the app would have shown you anyway.
 */
data class SongChord(val sounding: Chord, val voicing: Voicing)

/** A named section — "Chorus" — and the chords it uses, in the order they were played. */
data class Part(val name: String, val chords: List<SongChord>)

/**
 * A song: which chords its parts use, and how you play them.
 *
 * Documentation, not an arrangement. There is no timing, no bar count, no repeat, and parts appear
 * once each — a part is a fact about the song ("the chorus is C Em7 G Em7"), not an event in a
 * performance. Arrangement is Rubber Ring's job; it has the audio to hang it on.
 *
 * ### The capo belongs to the song
 *
 * [capo] is the fret the song was captured at, and it is *not* the live `Settings.capo`, which is a
 * preference for detection. It has to be stored here because every [SongChord.voicing] counts its
 * frets from the capo it was played behind: render the same song at a different capo and the shapes
 * become nonsense. So a song is viewed at its own capo, and changing it is not offered — the honest
 * implementation would discard every voicing choice in the song.
 */
data class Song(
    val id: String,
    val title: String,
    val capo: Int,
    val parts: List<Part> = emptyList(),
    /**
     * Whatever the chords cannot say: a tuning, a strumming pattern, where the second verse
     * goes quiet. Free text on purpose — the things worth remembering about a song are not a
     * list the app could have guessed.
     */
    val comment: String = "",
    val updatedAt: Long = 0L,
)

/**
 * Add [part], or replace the part of the same name.
 *
 * Part names are unique within a song, and this is that rule: one function, so the editor cannot
 * grow a second opinion about it.
 */
fun Song.upsertPart(part: Part): Song {
    val index = parts.indexOfFirst { it.name == part.name }
    return copy(
        parts = if (index < 0) parts + part
        else parts.toMutableList().also { it[index] = part },
    )
}

/** Drop the part called [name], if there is one. */
fun Song.removePart(name: String): Song = copy(parts = parts.filterNot { it.name == name })

/**
 * Copy the part called [name], landing the copy directly beneath it.
 *
 * For the second verse that is the first verse with one chord changed: capturing it again means
 * playing it again, and the whole part is already written down correctly bar a chord.
 *
 * The copy is numbered rather than named by you, because a part cannot be nameless even for the
 * moment a dialog is open, and the number is right often enough ("Verse 2") to leave alone. Renaming
 * a part is not something the editor offers, so this is also the only way to a second verse — which
 * is an argument for the number being a decent guess, not for asking.
 */
fun Song.duplicatePart(name: String): Song {
    val index = parts.indexOfFirst { it.name == name }
    if (index < 0) return this
    val duplicate = parts[index].copy(name = nextPartName(name, parts.mapTo(mutableSetOf()) { it.name }))
    return copy(parts = parts.toMutableList().also { it.add(index + 1, duplicate) })
}

/** "Verse" becomes "Verse 2"; "Verse 2" becomes "Verse 3"; both step over names already [taken]. */
private fun nextPartName(name: String, taken: Set<String>): String {
    val numbered = Regex("""^(.*?)\s+(\d+)$""").matchEntire(name)
    val base = numbered?.groupValues?.get(1) ?: name
    var n = (numbered?.groupValues?.get(2)?.toIntOrNull() ?: 1) + 1
    while ("$base $n" in taken) n++
    return "$base $n"
}

/**
 * Move the part at [from] by [offset] places, as far as the list allows.
 *
 * Parts are captured in whatever order you happened to play them, which is rarely the order they
 * belong in — the song reads intro, verse, chorus whether or not that is how the afternoon went.
 * This orders the *document*; it is still not an arrangement, because a part appears once and
 * nothing repeats.
 *
 * Moving off either end does nothing rather than wrapping around: the handle is held down by a
 * thumb, and a part leaping from the top to the bottom is never what was meant.
 */
fun Song.movePart(from: Int, offset: Int): Song {
    val to = from + offset
    if (from !in parts.indices || to !in parts.indices) return this
    return copy(parts = parts.toMutableList().also { it.add(to, it.removeAt(from)) })
}
