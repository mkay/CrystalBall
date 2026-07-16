package de.singular.crystalball.audio

import kotlin.math.sqrt

/**
 * The chord vocabulary as harmonic chroma templates — RubberRing's model, widened from 4 qualities
 * to 7.
 *
 * Each chord tone excites a series of overtones, so a real chord deposits energy well beyond its
 * own notes. Modelling that pattern — and scoring the whole 12-bin chroma by correlation — lets
 * quality be decided from the full harmonic fingerprint rather than a single (noisy) bin, which is
 * where bare note masks flip e.g. D into Dm.
 *
 * Templates are mean-centred and unit-normalised, so a dot product with a mean-centred chroma
 * vector is exactly a Pearson correlation.
 */
object ChordTemplates {

    /** Every (root, quality) pair, in label order: `label = root * qualities + quality.ordinal`. */
    val CHORDS: List<Chord> = buildList {
        for (root in 0 until 12) for (q in Quality.entries) add(Chord(root, q))
    }

    val COUNT = CHORDS.size // 84

    /**
     * Pitch class of the n-th harmonic relative to its fundamental: harmonics 1/2/4 are octaves (0),
     * 3/6 a fifth (7), 5 a major third (4). The 7th harmonic is dropped — it lands about a third of
     * a semitone flat and only muddies the pitch classes. Weights roll off with harmonic number.
     */
    private val HARMONIC_OFFSETS = intArrayOf(0, 0, 7, 0, 4, 7)
    private val HARMONIC_WEIGHTS = floatArrayOf(1f, 0.6f, 0.36f, 0.216f, 0.13f, 0.078f)

    /** The [COUNT] templates, indexed by label. */
    val TEMPLATES: Array<FloatArray> = Array(COUNT) { label ->
        val t = FloatArray(Chromagram.CHROMA_BINS)
        for (tone in CHORDS[label].tones) {
            for (n in HARMONIC_OFFSETS.indices) {
                t[(tone + HARMONIC_OFFSETS[n]) % Chromagram.CHROMA_BINS] += HARMONIC_WEIGHTS[n]
            }
        }
        centreAndNormalise(t)
        t
    }

    /**
     * Per-label prior added to each template's correlation, keeping the vocabulary honest about the
     * fact that its members are not equally likely and not equally distinguishable.
     *
     * Sevenths share three of their four notes with a triad, so without a nudge toward the simpler
     * chord, noise in the seventh bin would sprinkle spurious 7ths over plain triads; a genuine
     * seventh (its fourth note actually present) clears the margin easily. Sus chords are penalised
     * harder still: they are rarer than triads, and each one's note set is *identical* to some other
     * sus chord's (Csus2 and Gsus4 are both {C,D,G}), so they are inherently unresolvable from
     * chroma shape alone — [ChordRecognizer] leans on bass evidence to separate them, and this
     * prior keeps them from displacing a plain triad on a tie.
     */
    val QUALITY_BIAS: FloatArray = FloatArray(COUNT) { label ->
        when (CHORDS[label].quality) {
            Quality.MAJ, Quality.MIN -> 0f
            Quality.DOM7, Quality.MAJ7, Quality.MIN7 -> -SEVENTH_BIAS
            Quality.SUS2, Quality.SUS4 -> -SUS_BIAS
        }
    }

    /** In Pearson units: how much a seventh must out-correlate the best triad by to be chosen. */
    private const val SEVENTH_BIAS = 0.06f

    /** As [SEVENTH_BIAS], for sus chords — larger, since they are rarer and mutually degenerate. */
    private const val SUS_BIAS = 0.10f

    /** Mean-centre and unit-normalise a template in place, so a dot product is a correlation. */
    private fun centreAndNormalise(t: FloatArray) {
        var mean = 0f
        for (b in t.indices) mean += t[b]
        mean /= t.size
        var norm = 0f
        for (b in t.indices) { t[b] -= mean; norm += t[b] * t[b] }
        val inv = if (norm > 0f) 1f / sqrt(norm) else 0f
        for (b in t.indices) t[b] *= inv
    }
}
