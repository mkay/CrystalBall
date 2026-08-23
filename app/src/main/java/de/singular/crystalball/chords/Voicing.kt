// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.chords

/** Standard tuning EADGBE, low to high, as pitch classes (0 = C). Index 0 is the low E string. */
val STANDARD_TUNING = intArrayOf(4, 9, 2, 7, 11, 4)

const val STRING_COUNT = 6

/** [Voicing.frets] entry for a string that is not played. */
const val MUTED = -1

/**
 * What a grip is called, aside from where on the neck it sits.
 *
 * A name, not a caption: "Em shape" is a sentence about a grip and has to be written differently in
 * another language, so what is kept here is the fact — which shape — and the words are chosen where
 * they are shown. The grips are named after the open chords they transpose, which is how players
 * name them; the two triad shapes are named after the strings they sit on, having no open chord.
 */
sealed interface ShapeKind {
    /** A movable grip, named for the open chord it is a transposition of — "Em", "A7", "Dmaj7". */
    data class Grip(val chordName: String) : ShapeKind

    /** The three-string triad on the top strings. */
    data object TopTriad : ShapeKind

    /** The three-string triad in the middle of the neck. */
    data object MiddleTriad : ShapeKind

    /**
     * A movable grip named by the string its root sits on — "6th-string root".
     *
     * How the extended chords have to be named, because they transpose no open chord: there is no
     * open 6/9 or m7b5 to call them after, so [Grip] would have to invent a name. Which string
     * carries the root is what a player reads off the diagram anyway, and it is how these shapes
     * are taught.
     *
     * [string] is the index into [frets] — 0 is the low E — while players count the other way,
     * so [guitarString] does the turning around and the low E is the 6th.
     */
    data class RootOnString(val string: Int) : ShapeKind {
        val guitarString: Int get() = STRING_COUNT - string
    }
}

/**
 * The shape's name in fixed English, for the places that record one rather than show one.
 *
 * Screens name a shape in the language they are running in; this is what a song file says, so that
 * a sheet written on one phone reads the same on the next.
 */
val ShapeKind.englishName: String
    get() = when (this) {
        is ShapeKind.Grip -> "$chordName shape"
        ShapeKind.TopTriad -> "top triad"
        ShapeKind.MiddleTriad -> "middle triad"
        is ShapeKind.RootOnString -> "${ordinal(guitarString)}-string root"
    }

/**
 * One way to fret a chord: a fret number per string, low E first.
 *
 * A value of 0 is an open string, [MUTED] is one that is not sounded, and anything else is a fret.
 * Values are absolute fret numbers, not relative to any barre — [baseFret] is derived for display.
 */
data class Voicing(
    val frets: IntArray,
    /**
     * The fret this grip is named after, read off the player's own neck — 0 for open position.
     *
     * Not derived from [frets], because a capo makes the two differ: the numbers in [frets] are
     * counted from the capo while the dots on the neck are not, so a shape held at its own 5th fret
     * behind a capo at 2 is the 7th fret to the player. Carrying the answer means whoever generated
     * the shape, who knew where the capo was, is the one who did the arithmetic.
     */
    val position: Int = 0,
    /**
     * The movable shape this grip is a transposition of — null for a curated open grip, which is a
     * transposition of nothing, and for a grip the library does not recognise.
     *
     * Not part of [equals]: two grips with the same frets are the same grip however they were
     * arrived at, which is what lets a curated shape dedupe against its generated twin.
     */
    val shape: ShapeKind? = null,
) {
    init {
        require(frets.size == STRING_COUNT) { "a voicing needs $STRING_COUNT strings, got ${frets.size}" }
    }

    /** The lowest fretted (non-open, non-muted) fret, or 0 if nothing is fretted. */
    val lowestFret: Int get() = frets.filter { it > 0 }.minOrNull() ?: 0

    /** The highest fret used, or 0 for an all-open/muted shape. */
    val highestFret: Int get() = frets.filter { it > 0 }.maxOrNull() ?: 0

    /** True when the shape sits at the nut and should be drawn with one. */
    val isOpenPosition: Boolean get() = highestFret <= OPEN_POSITION_FRETS

    /**
     * First fret of the drawn window. Open-position shapes start at the nut; shapes further up
     * start far enough back to show the whole grip.
     */
    val baseFret: Int get() = if (isOpenPosition) 1 else lowestFret

    /**
     * The compact form [parse] reads back, e.g. "x32010" or "10-12-12-11-10-10".
     *
     * This is what a stored voicing *is*: the frets are the shape's identity (they are what
     * [equals] compares), so a song records them rather than an index into [ChordLibrary], which
     * would be a promise that the library never changes.
     */
    val spec: String
        get() {
            val tokens = frets.map { if (it == MUTED) "x" else it.toString() }
            // Single digits need no separators; anything wider must have them to stay readable back.
            return if (tokens.all { it.length == 1 }) tokens.joinToString("")
            else tokens.joinToString("-")
        }

    /**
     * The caption in fixed English: "open", "open · Am shape", "5th fret · E shape".
     *
     * This is the form a song file records, so a sheet written on one phone reads the same on the
     * next whatever either device is set to. Screens compose their own caption from [position] and
     * [shape] instead, in the language the app is running in.
     */
    val label: String
        get() {
            val where = if (position == 0) "open" else "${ordinal(position)} fret"
            return listOfNotNull(where, shape?.englishName).joinToString(" · ")
        }

    /** The pitch classes this voicing actually sounds. */
    fun soundedPitchClasses(tuning: IntArray = STANDARD_TUNING): Set<Int> =
        buildSet {
            for (string in frets.indices) {
                if (frets[string] != MUTED) add((tuning[string] + frets[string]) % 12)
            }
        }

    /** The pitch class of the lowest sounded string — the note a listener hears as the bass. */
    fun bassPitchClass(tuning: IntArray = STANDARD_TUNING): Int? =
        frets.indices.firstOrNull { frets[it] != MUTED }?.let { (tuning[it] + frets[it]) % 12 }

    // Array fields break data-class equality; compare by content so voicings dedupe correctly.
    override fun equals(other: Any?): Boolean =
        this === other || (other is Voicing && frets.contentEquals(other.frets))

    override fun hashCode(): Int = frets.contentHashCode()

    companion object {
        /** A shape reaching no higher than this is drawn in open position. */
        const val OPEN_POSITION_FRETS = 4

        /** How many frets a diagram shows. Wide enough for every shape the library produces. */
        const val WINDOW_FRETS = 5

        /**
         * Parse a compact shape string like "x32010" or "10-12-12-11-10-10". Single digits may be
         * written without separators; anything with two-digit frets must use "-".
         *
         * The result knows only what the frets say: its [position] is where the hand goes, given
         * [capo], and it claims no [shape]. A grip read back from a song is described properly by
         * [ChordLibrary.describe], which can recognise it; this is the honest fallback for one it
         * cannot.
         */
        fun parse(spec: String, capo: Int = 0): Voicing {
            val tokens =
                if (spec.contains('-')) spec.split('-')
                else spec.map { it.toString() }
            val frets = tokens.map { token ->
                if (token.equals("x", ignoreCase = true)) MUTED
                else token.toIntOrNull() ?: error("bad fret '$token' in shape '$spec'")
            }
            val lowest = frets.filter { it > 0 }.minOrNull() ?: 0
            return Voicing(
                frets.toIntArray(),
                position = if (lowest == 0) 0 else lowest + capo,
            )
        }

    }
}

/** English ordinals, for the fixed-English forms only — screens use ICU, see VoicingCaption. */
private fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
