package de.singular.crystalball.songs

import de.singular.crystalball.Capo
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.ChordCandidate
import de.singular.crystalball.chords.ChordLibrary
import de.singular.crystalball.chords.Voicing

/**
 * One chord as captured, before it is committed to a part.
 *
 * This mirrors `DetectState.Result`, which is the point: reviewing a captured chord is the same
 * question the result page already answers — is this what I played, and how do I play it — so it
 * carries the same ranked [candidates] and follows the user's correction the same way.
 *
 * The candidates live only as long as the capture session. They are what makes fixing a misread one
 * tap while you still remember playing it, and that is when misreads get fixed; a saved song keeps
 * the chord, not the recogniser's opinion of it. See [SongJson].
 */
data class CapturedChord(
    /** Ranked best-first, as the recogniser offered them. Never empty. */
    val candidates: List<ChordCandidate>,
    /** The sounding chord: the best fit, until the user says otherwise. */
    val selected: Chord = candidates.first().chord,
    /**
     * The grip to document, or null for "whatever the library leads with".
     *
     * Nullable rather than eagerly resolved because null is a real state and not a missing value:
     * it means the user has not overridden anything, so the choice should keep following the
     * chord — which matters when [selected] changes, because a grip chosen for the old chord would
     * otherwise document a shape that does not sound what the name says.
     */
    val voicing: Voicing? = null,
) {
    /** The runner-ups, for the "did you mean" row. */
    val alternatives: List<ChordCandidate>
        get() = candidates.filter { it.chord != selected }.take(ALTERNATIVE_COUNT)

    /** Resolve to what gets written down, at the song's [capo]. */
    fun toSongChord(capo: Int): SongChord =
        SongChord(selected, voicing ?: defaultVoicing(selected, capo))

    private companion object {
        const val ALTERNATIVE_COUNT = 4
    }
}

/** The grip the app would have shown you anyway: the library's first choice for this chord. */
fun defaultVoicing(sounding: Chord, capo: Int): Voicing =
    ChordLibrary.voicingsFor(Capo.shapeChord(sounding, capo), capo).first()
