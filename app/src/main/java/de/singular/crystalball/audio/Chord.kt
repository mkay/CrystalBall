// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.audio

/**
 * How the twelve pitch classes are spelled.
 *
 * The difference is one letter, and it is the letter German-speaking musicians grew up with: what
 * the rest of the world calls B, German notation calls H, and B is used for what elsewhere is B
 * flat. A display choice only — a [Chord] is a pitch-class number either way, so switching this
 * re-spells the chords already written down rather than changing them.
 *
 * Not inferred from the app's language. Plenty of German speakers read tabs in international
 * notation and plenty of players elsewhere were taught H, so this is a preference of its own.
 */
enum class NoteNaming { INTERNATIONAL, GERMAN }

/** 0 = C, 1 = C#, … 11 = B. */
val ROOT_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** The same twelve, spelled the German way: 10 is B (= A#/B flat) and 11 is H. */
private val ROOT_NAMES_DE =
    arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "B", "H")

/** The root names for [naming], indexed by pitch class. */
fun rootNames(naming: NoteNaming): Array<String> =
    if (naming == NoteNaming.GERMAN) ROOT_NAMES_DE else ROOT_NAMES

/**
 * A chord quality — and whether the microphone gets a say in it.
 *
 * Two vocabularies share this one enum, and the difference between them is [detectable]. Those are
 * the triads and sevenths a single-mic chromagram can actually tell apart, and the only ones
 * [ChordTemplates] builds a template for. The rest are drawn but never heard: denser extensions
 * (6ths, 9ths, altered dominants) overlap those templates too heavily to survive matching, so
 * scoring them would cost accuracy on the chords people actually play — while a player still
 * needs to look one up, and to write into a song a chord the recogniser was never going to offer.
 *
 * So the flag is not a to-do. Marking a quality undetectable is a statement about the microphone
 * and nothing else; it says the chord is out of earshot, not that it is out of the library, which
 * is why ChordLibrary.allChords takes the whole enum on purpose.
 *
 * [suffix] is what a chord name is built from ("Am" needs the bare "m"); [label] is what a chooser
 * shows on its own, where an empty string would be a blank button.
 *
 * Declaration order is the order templates are built in, and the order the quality chips appear in.
 * Both are derived from this list rather than recorded anywhere — a song stores a quality by
 * name, see SongJson — so reordering is safe, and the user can see it.
 */
enum class Quality(val suffix: String, val label: String, val detectable: Boolean = true) {
    MAJ("", "maj"),
    MIN("m", "min"),
    DOM7("7", "7"),
    MAJ7("maj7", "maj7"),
    MIN7("m7", "m7"),
    SUS2("sus2", "sus2"),
    SUS4("sus4", "sus4"),
    ;

    companion object {
        /**
         * The qualities the recogniser scores, in declaration order.
         *
         * [ChordTemplates] enumerates this rather than [entries], which is the whole of the split:
         * adding a quality to the enum widens what the app can draw, and only setting [detectable]
         * widens what it can hear.
         */
        val DETECTABLE: List<Quality> = entries.filter { it.detectable }
    }
}

/**
 * A recognised chord: a root pitch class (0 = C) and a quality.
 *
 * Unlike RubberRing's equivalent there is no `NONE` member — this app either has a chord to show or
 * is still listening, and that distinction lives in the UI state, not in the chord type.
 */
data class Chord(val root: Int, val quality: Quality) {
    /**
     * Display name, e.g. "C", "Am", "G7", "Dsus4", spelling the root according to [naming].
     *
     * The default is international, which is also what the shape tables are keyed by — those are
     * data and must not move when the preference does.
     */
    fun name(naming: NoteNaming = NoteNaming.INTERNATIONAL): String =
        rootNames(naming)[root] + quality.suffix

    /** The international spelling, for the places that are matching a symbol rather than showing one. */
    val name: String get() = name(NoteNaming.INTERNATIONAL)

    /** The chord's pitch classes (root-relative intervals folded into 0..11). */
    val tones: IntArray get() = IntArray(intervals.size) { (root + intervals[it]) % 12 }

    /** Semitone intervals above the root, per quality. */
    private val intervals: IntArray
        get() = when (quality) {
            Quality.MAJ -> intArrayOf(0, 4, 7)
            Quality.MIN -> intArrayOf(0, 3, 7)
            Quality.DOM7 -> intArrayOf(0, 4, 7, 10)
            Quality.MAJ7 -> intArrayOf(0, 4, 7, 11)
            Quality.MIN7 -> intArrayOf(0, 3, 7, 10)
            Quality.SUS2 -> intArrayOf(0, 2, 7)
            Quality.SUS4 -> intArrayOf(0, 5, 7)
        }
}
