// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball

import de.singular.crystalball.audio.Chord
import de.singular.crystalball.chords.ShapeKind

/** Which name leads when a capo makes the chord you finger differ from the chord you hear. */
enum class NameStyle {
    /** "E", with "D, E shape · capo 2" beneath — what the band hears, leading. */
    SOUNDING_FIRST,

    /** "D", with "sounds as E · capo 2" beneath — what your hands are doing, leading. */
    SHAPE_FIRST,
}

/** How the app picks its light/dark colours: follow the OS, or force one. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The second name under a chord, as facts rather than as a sentence.
 *
 * Which of the two it is depends on [NameStyle]; what either one reads like depends on the language
 * the app is in, so the words are chosen on screen and only the chord and the shape are decided
 * here.
 */
sealed interface ShapeLine {
    /** The chord you finger, and the movable shape of the grip shown — "D, A shape". */
    data class Fingered(val chord: Chord, val shape: ShapeKind?) : ShapeLine

    /** What the shape you are fingering comes out as — "sounds as E". */
    data class Sounds(val chord: Chord) : ShapeLine
}

/** User preferences, persisted across launches. */
data class Settings(
    /** Fret the capo is clamped at; 0 for none. */
    val capo: Int = 0,
    val nameStyle: NameStyle = NameStyle.SOUNDING_FIRST,
    /** Hold the display awake — you are holding a guitar, not the phone. Off by default: battery. */
    val keepScreenOn: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Open the capo sheet on launch, for players who clamp a capo somewhere new most sessions.
     * On by default: a capo left set from yesterday is the one piece of stale state that makes
     * every diagram wrong, and the sheet costs a single tap — picking a fret closes it again.
     */
    val showCapoOnStart: Boolean = true,
)

/**
 * Capo arithmetic.
 *
 * A capo changes nothing about detection: the microphone hears real pitches, and a D shape behind a
 * capo at the 2nd fret genuinely *is* an E chord — that is what a tuner, a listener, and this app's
 * recogniser all report. What it changes is which shape your hands make, and that is purely a
 * matter of what the app draws.
 *
 * The rule is simply that a capo is a movable nut: everything shifts up by [Settings.capo] frets, so
 * to sound a chord you finger the shape of the chord [capo] semitones below it and count frets from
 * the capo. That holds for open grips and barres alike, which is why the diagrams need no special
 * case beyond drawing the capo where the nut goes.
 */
object Capo {

    /**
     * Highest capo position offered.
     *
     * Seven, because that is the last fret at which *every* chord in the vocabulary still has a
     * shape that fits on the neck — frets are counted from the capo, so a capo at 8 pushes the only
     * G#sus2 shape past the 15th fret, and at 9 two more follow it. Rather than offer a position
     * that can leave the screen with nothing to draw, the range stops where the guitar does. Capo 7
     * still covers the high-capo repertoire people actually play.
     */
    const val MAX_FRET = 7

    /** The chord to finger, counting from the capo, so that [sounding] comes out. */
    fun shapeChord(sounding: Chord, capo: Int): Chord =
        if (capo == 0) sounding
        else Chord(((sounding.root - capo) % 12 + 12) % 12, sounding.quality)

    /** The name shown large. */
    fun title(sounding: Chord, capo: Int, style: NameStyle): String = when {
        capo == 0 -> sounding.name
        style == NameStyle.SOUNDING_FIRST -> sounding.name
        else -> shapeChord(sounding, capo).name
    }

    /**
     * The other name — the chord you finger, or what it sounds — without saying where the capo is.
     *
     * Null when there is no capo and the two names agree, so there is no second name to give.
     *
     * [shape] is the movable shape of the grip currently shown large, and is what makes this line
     * track the promoted variation instead of standing still. It matters because the chord name
     * alone reads as a grip and is not one: the minor shapes are named Em/Am/Dm, so a capo'd Dm was
     * headed "Cm" above variations captioned "Am shape" — naming a grip that appears nowhere in the
     * list. Null for a curated open grip, which is a transposition of no movable shape; the clause
     * is then dropped rather than guessed at.
     *
     * Returns the parts rather than the sentence. "sounds as E" is prose and the word order is not
     * ours to fix — this layer knows the facts, and the screen puts them into words. See
     * `chordShapeLine` in the UI, which is also where the capo is appended for the pages that do
     * not state it themselves.
     */
    fun shapeLine(
        sounding: Chord,
        capo: Int,
        style: NameStyle,
        shape: ShapeKind? = null,
    ): ShapeLine? = when {
        capo == 0 -> null
        style == NameStyle.SOUNDING_FIRST -> ShapeLine.Fingered(shapeChord(sounding, capo), shape)
        else -> ShapeLine.Sounds(sounding)
    }

    /** How a chord is named where there is no room for a subtitle, e.g. the alternatives row. */
    fun shortName(sounding: Chord, capo: Int, style: NameStyle): String = when {
        capo == 0 || style == NameStyle.SOUNDING_FIRST -> sounding.name
        else -> shapeChord(sounding, capo).name
    }
}
