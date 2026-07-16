package de.singular.crystalball.chords

/** Standard tuning EADGBE, low to high, as pitch classes (0 = C). Index 0 is the low E string. */
val STANDARD_TUNING = intArrayOf(4, 9, 2, 7, 11, 4)

const val STRING_COUNT = 6

/** [Voicing.frets] entry for a string that is not played. */
const val MUTED = -1

/**
 * One way to fret a chord: a fret number per string, low E first.
 *
 * A value of 0 is an open string, [MUTED] is one that is not sounded, and anything else is a fret.
 * Values are absolute fret numbers, not relative to any barre — [baseFret] is derived for display.
 */
data class Voicing(
    val frets: IntArray,
    /** Short position label, e.g. "open" or "5th fret". */
    val label: String,
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
         */
        fun parse(spec: String, label: String): Voicing {
            val tokens =
                if (spec.contains('-')) spec.split('-')
                else spec.map { it.toString() }
            val frets = tokens.map { token ->
                if (token.equals("x", ignoreCase = true)) MUTED
                else token.toIntOrNull() ?: error("bad fret '$token' in shape '$spec'")
            }
            return Voicing(frets.toIntArray(), label)
        }
    }
}
