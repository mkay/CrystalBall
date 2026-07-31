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
 * A chord quality. The triads and sevenths a single-mic chromagram can actually tell apart:
 * denser extensions (9ths, 6ths, altered dominants) overlap the templates below too heavily to
 * survive template matching, so they are deliberately out of scope.
 *
 * The declaration order *is* the template order — [ChordTemplates] indexes qualities by [ordinal],
 * so reordering this enum silently reshuffles every template. Append, don't insert.
 */
enum class Quality(val suffix: String, val label: String) {
    MAJ("", "maj"),
    MIN("m", "min"),
    DOM7("7", "7"),
    MAJ7("maj7", "maj7"),
    MIN7("m7", "m7"),
    SUS2("sus2", "sus2"),
    SUS4("sus4", "sus4"),
    ;

    /**
     * [suffix] is what a chord name is built from ("Am" needs the bare "m"); [label] is what a
     * chooser shows on its own, where an empty string would be a blank button.
     */
    companion object
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
