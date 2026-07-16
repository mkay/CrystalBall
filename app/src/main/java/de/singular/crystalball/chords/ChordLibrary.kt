package de.singular.crystalball.chords

import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.audio.ROOT_NAMES

/**
 * Guitar shapes for any chord in the vocabulary, in standard tuning.
 *
 * Two sources, deliberately. The **curated** table holds the open-position shapes a player actually
 * expects to see — the ones with names, that no scoring function would reliably pick out of the set
 * of technically-valid fingerings. Everything else is **generated** by transposing movable (CAGED)
 * forms up the neck, which covers all 84 chords, including the ones with no open shape at all.
 *
 * Curated shapes always rank first; generated ones fill in behind, nearest the nut first, so the
 * "other variations" row walks up the neck.
 */
object ChordLibrary {

    /**
     * A muted string in a [MovableShape]'s offsets.
     *
     * Deliberately *not* [MUTED]: offsets are relative and legitimately negative (the C and G forms
     * reach back behind their root), so -1 has to mean "one fret below the root" and cannot double
     * as the mute sentinel. A value no real offset can take keeps the two apart.
     */
    private const val X = Int.MIN_VALUE

    /**
     * A movable form: fret offsets relative to the root, with the root on [rootString].
     *
     * Offsets are added to the fret at which [rootString] gives the chord's root, so the whole form
     * slides up the neck. [X] strings stay muted. These are exactly the open shapes below, read
     * relative to their root — the E form is E/Em/E7 barred, the A form A/Am/A7, and so on.
     *
     * Offsets may be **negative**: in the C and G forms the root is not the lowest fretted note, so
     * the rest of the grip sits behind it. Such a form is simply unreachable when the root lands too
     * near the nut for it to fit, and [generate] drops it there.
     */
    private class MovableShape(val rootString: Int, val offsets: IntArray, val name: String)

    /** Movable forms per quality. Order is the fallback ranking: E form, then A, then D, then C, G. */
    private val MOVABLE: Map<Quality, List<MovableShape>> = mapOf(
        Quality.MAJ to listOf(
            MovableShape(0, intArrayOf(0, 2, 2, 1, 0, 0), "E form"),
            MovableShape(1, intArrayOf(X, 0, 2, 2, 2, 0), "A form"),
            MovableShape(2, intArrayOf(X, X, 0, 2, 3, 2), "D form"),
            MovableShape(1, intArrayOf(X, 0, -1, -3, -2, -3), "C form"),
            MovableShape(0, intArrayOf(0, -1, -3, -3, -3, 0), "G form"),
            MovableShape(3, intArrayOf(X, X, X, 0, 0, -2), "top triad"),
            MovableShape(2, intArrayOf(X, X, 0, -1, -2, X), "middle triad"),
        ),
        Quality.MIN to listOf(
            MovableShape(0, intArrayOf(0, 2, 2, 0, 0, 0), "Em form"),
            MovableShape(1, intArrayOf(X, 0, 2, 2, 1, 0), "Am form"),
            MovableShape(2, intArrayOf(X, X, 0, 2, 3, 1), "Dm form"),
            MovableShape(3, intArrayOf(X, X, X, 0, -1, -2), "top triad"),
            MovableShape(2, intArrayOf(X, X, 0, -2, -2, X), "middle triad"),
        ),
        Quality.DOM7 to listOf(
            MovableShape(0, intArrayOf(0, 2, 0, 1, 0, 0), "E7 form"),
            MovableShape(1, intArrayOf(X, 0, 2, 0, 2, 0), "A7 form"),
            MovableShape(2, intArrayOf(X, X, 0, 2, 1, 2), "D7 form"),
            MovableShape(1, intArrayOf(X, 0, -1, 0, -2, -3), "C7 form"),
            MovableShape(0, intArrayOf(0, -1, -3, -3, -3, -2), "G7 form"),
        ),
        Quality.MAJ7 to listOf(
            MovableShape(0, intArrayOf(0, 2, 1, 1, 0, 0), "Emaj7 form"),
            MovableShape(1, intArrayOf(X, 0, 2, 1, 2, 0), "Amaj7 form"),
            MovableShape(2, intArrayOf(X, X, 0, 2, 2, 2), "Dmaj7 form"),
            MovableShape(1, intArrayOf(X, 0, -1, -3, -3, -3), "Cmaj7 form"),
            MovableShape(0, intArrayOf(0, -1, -3, -3, -3, -1), "Gmaj7 form"),
        ),
        Quality.MIN7 to listOf(
            MovableShape(0, intArrayOf(0, 2, 0, 0, 0, 0), "Em7 form"),
            MovableShape(1, intArrayOf(X, 0, 2, 0, 1, 0), "Am7 form"),
            MovableShape(2, intArrayOf(X, X, 0, 2, 1, 1), "Dm7 form"),
        ),
        Quality.SUS2 to listOf(
            MovableShape(1, intArrayOf(X, 0, 2, 2, 0, 0), "Asus2 form"),
            MovableShape(2, intArrayOf(X, X, 0, 2, 3, 0), "Dsus2 form"),
            MovableShape(0, intArrayOf(0, 2, 4, 4, X, X), "Esus2 form"),
        ),
        Quality.SUS4 to listOf(
            MovableShape(0, intArrayOf(0, 2, 2, 2, 0, 0), "Esus4 form"),
            MovableShape(1, intArrayOf(X, 0, 2, 2, 3, 0), "Asus4 form"),
            MovableShape(2, intArrayOf(X, X, 0, 2, 3, 3), "Dsus4 form"),
        ),
    )

    /**
     * Open-position shapes worth naming, keyed by chord name. Every entry is a standard grip; the
     * unit tests verify each one sounds exactly its chord's notes, so a typo here cannot ship.
     */
    private val CURATED: Map<String, List<String>> = mapOf(
        "C" to listOf("x32010"),
        "A" to listOf("x02220"),
        "G" to listOf("320003"),
        "E" to listOf("022100"),
        "D" to listOf("xx0232"),
        "Am" to listOf("x02210"),
        "Em" to listOf("022000"),
        "Dm" to listOf("xx0231"),
        "A7" to listOf("x02020"),
        "B7" to listOf("x21202"),
        "C7" to listOf("x32310"),
        "D7" to listOf("xx0212"),
        "E7" to listOf("020100"),
        "G7" to listOf("320001"),
        "Amaj7" to listOf("x02120"),
        "Cmaj7" to listOf("x32000"),
        "Dmaj7" to listOf("xx0222"),
        "Emaj7" to listOf("021100"),
        "Fmaj7" to listOf("xx3210"),
        "Gmaj7" to listOf("320002"),
        "Am7" to listOf("x02010"),
        "Dm7" to listOf("xx0211"),
        "Em7" to listOf("020000"),
        "Asus2" to listOf("x02200"),
        "Dsus2" to listOf("xx0230"),
        "Csus2" to listOf("x30013"),
        "Asus4" to listOf("x02230"),
        "Dsus4" to listOf("xx0233"),
        "Esus4" to listOf("022200"),
        "Gsus4" to listOf("330013"),
    )

    /** Highest fret any generated shape may reach — past this it is off the end of most necks. */
    private const val MAX_FRET = 15

    /**
     * Shapes for [chord], best first: curated open grips, then movable forms walking up the neck.
     *
     * [capo] shifts the whole hand up the neck, because every fret in a shape is counted from the
     * capo — so a shape reaching the 13th fret is really at the 15th with a capo at 2, and one that
     * is a stretch on an open neck can run off the end entirely. Shapes that no longer fit are
     * dropped rather than offered, since a diagram you cannot physically reach is worse than no
     * diagram.
     *
     * Never empty: `every chord keeps a playable shape at every capo position` proves it for the
     * whole vocabulary, so callers may take the first freely.
     */
    fun voicingsFor(chord: Chord, capo: Int = 0): List<Voicing> {
        val out = LinkedHashSet<Voicing>() // insertion-ordered, and dedupes generated vs curated
        CURATED[chord.name]?.forEach { out.add(Voicing.parse(it, "open")) }
        out.addAll(generate(chord, capo))
        return out.filter { it.highestFret + capo <= MAX_FRET }
    }

    /** Transpose every movable form for the quality to the chord's root, at each reachable octave. */
    private fun generate(chord: Chord, capo: Int): List<Voicing> {
        val shapes = MOVABLE[chord.quality].orEmpty()
        val found = ArrayList<Voicing>()
        for (shape in shapes) {
            // Fret on the root string that gives the chord's root, plus the octave above it.
            val base = ((chord.root - STANDARD_TUNING[shape.rootString]) % 12 + 12) % 12
            for (position in intArrayOf(base, base + 12)) {
                // Validate before mapping to fret numbers: once a fret is written out, a computed
                // -1 would be indistinguishable from MUTED and the string would vanish silently.
                val reachable = shape.offsets.all { offset ->
                    offset == X || (offset + position in 0..MAX_FRET)
                }
                if (!reachable) continue
                val frets = IntArray(STRING_COUNT) { s ->
                    if (shape.offsets[s] == X) MUTED else shape.offsets[s] + position
                }
                found.add(Voicing(frets, label = positionLabel(frets, shape.name, capo)))
            }
        }
        // Nearest the nut first — that is the shape a player reaches for.
        return found.sortedBy { it.lowestFret }
    }

    /**
     * Where the shape sits, named by the fret you actually put your finger on.
     *
     * The [capo] is added because the fret numbers in a shape are counted from it, while the marks
     * on the neck are not: with a capo at 2, a shape held at its own 5th fret is at the guitar's
     * 7th, and telling a player "5th fret" leaves them counting frets from a capo instead of
     * reading the dots on their own fretboard.
     */
    private fun positionLabel(frets: IntArray, shapeName: String, capo: Int): String {
        val lowest = frets.filter { it > 0 }.minOrNull() ?: 0
        return if (lowest == 0) "open · $shapeName" else "${ordinal(lowest + capo)} fret · $shapeName"
    }

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

    /** Every chord the library can draw — the vocabulary, for tests and future browsing UI. */
    fun allChords(): List<Chord> =
        ROOT_NAMES.indices.flatMap { root -> Quality.entries.map { Chord(root, it) } }
}
