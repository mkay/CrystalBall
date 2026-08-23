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
 * and nothing else — the chord is out of earshot, not out of the library, and nothing downstream
 * of here consults it when deciding what can be drawn or written into a song.
 *
 * Several of the extensions are not merely hard to hear but *impossible*: a C6 and an Am7 are the
 * same four pitch classes, so are a Cm6 and an Am7b5, and a diminished seventh is the same chord
 * four times over. No chromagram resolves those, and no amount of work on the recogniser will.
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

    // Everything below is drawn but never heard. The rule is unaltered extensions only: that is a
    // closed family you can enumerate and be done with, where alterations are not — admit 7b9 and
    // you owe 7#9, 7#11, 7b13 and every combination, with no principled place to stop.
    DIM("dim", "dim", detectable = false),
    AUG("aug", "aug", detectable = false),
    MIN7B5("m7b5", "m7b5", detectable = false),
    DIM7("dim7", "dim7", detectable = false),
    DOM7SUS4("7sus4", "7sus4", detectable = false),
    SIX("6", "6", detectable = false),
    MIN6("m6", "m6", detectable = false),
    ADD9("add9", "add9", detectable = false),
    DOM9("9", "9", detectable = false),
    MAJ9("maj9", "maj9", detectable = false),
    MIN9("m9", "m9", detectable = false),
    SIX_NINE("6/9", "6/9", detectable = false),
    DOM13("13", "13", detectable = false),
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
            Quality.DIM -> intArrayOf(0, 3, 6)
            Quality.AUG -> intArrayOf(0, 4, 8)
            Quality.MIN7B5 -> intArrayOf(0, 3, 6, 10)
            Quality.DIM7 -> intArrayOf(0, 3, 6, 9)
            Quality.DOM7SUS4 -> intArrayOf(0, 5, 7, 10)
            Quality.SIX -> intArrayOf(0, 4, 7, 9)
            Quality.MIN6 -> intArrayOf(0, 3, 7, 9)
            Quality.ADD9 -> intArrayOf(0, 2, 4, 7)
            Quality.DOM9 -> intArrayOf(0, 2, 4, 7, 10)
            Quality.MAJ9 -> intArrayOf(0, 2, 4, 7, 11)
            Quality.MIN9 -> intArrayOf(0, 2, 3, 7, 10)
            Quality.SIX_NINE -> intArrayOf(0, 2, 4, 7, 9)
            // The ninth a full 13th chord also carries is left out: on six strings it is the first
            // note to go, and a shape that dropped it would then be sounding a note the chord does
            // not claim. Root, third, seventh and the thirteenth are what makes it one.
            Quality.DOM13 -> intArrayOf(0, 4, 7, 9, 10)
        }
}
