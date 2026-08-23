// SPDX-License-Identifier: GPL-3.0-only

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
 * shapes up the neck, which covers all 84 chords, including the ones with no open shape at all.
 *
 * Curated shapes always rank first; generated ones fill in behind, nearest the nut first, so the
 * "other variations" row walks up the neck.
 */
object ChordLibrary {

    /**
     * A muted string in a [MovableShape]'s offsets.
     *
     * Deliberately *not* [MUTED]: offsets are relative and legitimately negative (the C and G shapes
     * reach back behind their root), so -1 has to mean "one fret below the root" and cannot double
     * as the mute sentinel. A value no real offset can take keeps the two apart.
     */
    private const val X = Int.MIN_VALUE

    /**
     * A movable shape: fret offsets relative to the root, with the root on [rootString].
     *
     * Offsets are added to the fret at which [rootString] gives the chord's root, so the whole shape
     * slides up the neck. [X] strings stay muted. These are exactly the open shapes below, read
     * relative to their root — the E shape is E/Em/E7 barred, the A shape A/Am/A7, and so on.
     *
     * Offsets may be **negative**: in the C and G shapes the root is not the lowest fretted note, so
     * the rest of the grip sits behind it. Such a shape is simply unreachable when the root lands too
     * near the nut for it to fit, and [generate] drops it there.
     */
    private class MovableShape(val rootString: Int, val offsets: IntArray, val kind: ShapeKind)

    /** Shorthand for the common case: a shape named after the open chord it transposes. */
    private fun grip(name: String) = ShapeKind.Grip(name)

    /** Movable shapes per quality. Order is the fallback ranking: E shape, then A, then D, then C, G. */
    private val MOVABLE: Map<Quality, List<MovableShape>> = mapOf(
        Quality.MAJ to listOf(
            MovableShape(0, intArrayOf(0, 2, 2, 1, 0, 0), grip("E")),
            MovableShape(1, intArrayOf(X, 0, 2, 2, 2, 0), grip("A")),
            MovableShape(2, intArrayOf(X, X, 0, 2, 3, 2), grip("D")),
            MovableShape(1, intArrayOf(X, 0, -1, -3, -2, -3), grip("C")),
            MovableShape(0, intArrayOf(0, -1, -3, -3, -3, 0), grip("G")),
            MovableShape(3, intArrayOf(X, X, X, 0, 0, -2), ShapeKind.TopTriad),
            MovableShape(2, intArrayOf(X, X, 0, -1, -2, X), ShapeKind.MiddleTriad),
        ),
        Quality.MIN to listOf(
            MovableShape(0, intArrayOf(0, 2, 2, 0, 0, 0), grip("Em")),
            MovableShape(1, intArrayOf(X, 0, 2, 2, 1, 0), grip("Am")),
            MovableShape(2, intArrayOf(X, X, 0, 2, 3, 1), grip("Dm")),
            MovableShape(3, intArrayOf(X, X, X, 0, -1, -2), ShapeKind.TopTriad),
            MovableShape(2, intArrayOf(X, X, 0, -2, -2, X), ShapeKind.MiddleTriad),
        ),
        Quality.DOM7 to listOf(
            MovableShape(0, intArrayOf(0, 2, 0, 1, 0, 0), grip("E7")),
            MovableShape(1, intArrayOf(X, 0, 2, 0, 2, 0), grip("A7")),
            MovableShape(2, intArrayOf(X, X, 0, 2, 1, 2), grip("D7")),
            MovableShape(1, intArrayOf(X, 0, -1, 0, -2, -3), grip("C7")),
            MovableShape(0, intArrayOf(0, -1, -3, -3, -3, -2), grip("G7")),
        ),
        Quality.MAJ7 to listOf(
            MovableShape(0, intArrayOf(0, 2, 1, 1, 0, 0), grip("Emaj7")),
            MovableShape(1, intArrayOf(X, 0, 2, 1, 2, 0), grip("Amaj7")),
            MovableShape(2, intArrayOf(X, X, 0, 2, 2, 2), grip("Dmaj7")),
            MovableShape(1, intArrayOf(X, 0, -1, -3, -3, -3), grip("Cmaj7")),
            MovableShape(0, intArrayOf(0, -1, -3, -3, -3, -1), grip("Gmaj7")),
        ),
        Quality.MIN7 to listOf(
            MovableShape(0, intArrayOf(0, 2, 0, 0, 0, 0), grip("Em7")),
            MovableShape(1, intArrayOf(X, 0, 2, 0, 1, 0), grip("Am7")),
            MovableShape(2, intArrayOf(X, X, 0, 2, 1, 1), grip("Dm7")),
        ),
        Quality.SUS2 to listOf(
            MovableShape(1, intArrayOf(X, 0, 2, 2, 0, 0), grip("Asus2")),
            MovableShape(2, intArrayOf(X, X, 0, 2, 3, 0), grip("Dsus2")),
            MovableShape(0, intArrayOf(0, 2, 4, 4, X, X), grip("Esus2")),
        ),
        Quality.SUS4 to listOf(
            MovableShape(0, intArrayOf(0, 2, 2, 2, 0, 0), grip("Esus4")),
            MovableShape(1, intArrayOf(X, 0, 2, 2, 3, 0), grip("Asus4")),
            MovableShape(2, intArrayOf(X, X, 0, 2, 3, 3), grip("Dsus4")),
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
     * Shapes for [chord], best first: curated open grips, then movable shapes walking up the neck.
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
        // Curated grips are open-position by definition — that is what earns them a place in the
        // table — so they are named for the nut rather than for the fret their lowest finger lands
        // on, capo or no capo.
        CURATED[chord.name]?.forEach { out.add(Voicing.parse(it).copy(position = 0)) }
        out.addAll(generate(chord, capo))
        return out.filter { it.highestFret + capo <= MAX_FRET }
    }

    /** Transpose every movable shape for the quality to the chord's root, at each reachable octave. */
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
                val lowest = frets.filter { it > 0 }.minOrNull() ?: 0
                found.add(
                    Voicing(
                        frets,
                        // Named for the fret the player reads off their own neck, which is why the
                        // capo is added: the frets in a shape are counted from it, the dots on the
                        // neck are not. With a capo at 2, a shape held at its own 5th fret is the
                        // guitar's 7th, and saying "5th" leaves them counting up from the capo.
                        position = if (lowest == 0) 0 else lowest + capo,
                        shape = shape.kind,
                    ),
                )
            }
        }
        // Nearest the nut first — that is the shape a player reaches for.
        return found.sortedBy { it.lowestFret }
    }

    /**
     * The library's own account of [voicing] as a grip for [shape] at [capo] — which shape it is,
     * and where it sits.
     *
     * A song records the frets and nothing else: they are the grip's identity, and everything said
     * about it is derivable from them, which is what keeps a sheet readable in a language it was
     * not written in. Recognising it here is the deriving. A grip the library does not offer — one
     * fingered by hand — is returned as it came, named by its position alone, which is all that can
     * honestly be said about it.
     */
    fun describe(voicing: Voicing, shape: Chord, capo: Int): Voicing =
        voicingsFor(shape, capo).firstOrNull { it == voicing } ?: voicing

    /**
     * The qualities this library has shapes for, in the enum's own order.
     *
     * Derived from [MOVABLE] rather than declared, because having a shape is what being drawable
     * *is*: [voicingsFor] promises never to come back empty, and callers take its first entry
     * without looking (see `defaultVoicing`). A quality with no movable shape would break that
     * promise the moment someone picked it, so it must not be offered at all.
     *
     * Which makes this the switch the extended qualities come up on, one at a time: a quality is
     * in the enum from the day its intervals are written down, and appears in the dictionary and
     * the chooser on the day it earns a grip. Nothing halfway is ever reachable.
     */
    val DRAWABLE: List<Quality> = Quality.entries.filter { MOVABLE.containsKey(it) }

    /** Every chord the library can draw — the dictionary's vocabulary, and the chooser's. */
    fun allChords(): List<Chord> =
        ROOT_NAMES.indices.flatMap { root -> DRAWABLE.map { Chord(root, it) } }
}
