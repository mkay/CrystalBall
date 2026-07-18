package de.singular.crystalball.audio

import kotlin.math.sqrt

/** A scored guess. [score] is a biased Pearson correlation, so roughly -1f..1f — higher is better. */
data class ChordCandidate(val chord: Chord, val score: Float)

/**
 * Ranks chord templates against accumulated chroma evidence.
 *
 * RubberRing classified each frame (or beat) independently and kept only the winner, because it was
 * labelling a timeline. Here the whole job is one chord, so evidence is *accumulated*: [add] folds
 * each frame into a running mean and [rank] classifies that mean. Averaging is what makes this
 * robust — a single frame catches whatever the strum happened to be doing (pick transient, one
 * string ringing louder), while the mean over a few hundred milliseconds settles onto the chord.
 * It also means the answer only sharpens as more audio arrives, which is what lets the caller stop
 * as soon as it is confident rather than at a fixed deadline.
 *
 * Not thread-safe; drive it from a single analysis thread.
 */
class ChordRecognizer {

    private val chromaSum = FloatArray(Chromagram.CHROMA_BINS)
    private val bassSum = FloatArray(Chromagram.CHROMA_BINS)

    /** Frames folded in since the last [reset]. */
    var frameCount = 0
        private set

    /** Fold one frame into the running evidence. */
    fun add(frame: ChromaFrame) {
        for (b in 0 until Chromagram.CHROMA_BINS) {
            chromaSum[b] += frame.chroma[b]
            bassSum[b] += frame.bass[b]
        }
        frameCount++
    }

    /**
     * How peaked the accumulated chroma is: the coefficient of variation across the twelve bins.
     *
     * A chord puts most of its energy in a few bins and scores high; room tone spreads energy over
     * all twelve and scores near zero. Deliberately scale-invariant — it is a statement about shape,
     * not loudness, so a quiet unplugged guitar across the room reads the same as a mic'd amp.
     *
     * This is the question [rank] cannot ask. Ranking divides the shape out by `sqrt(variance)`, so
     * a chroma with almost no shape still normalises up to a full-scale correlation and some chord
     * always wins. Measure the shape *before* it is normalised away.
     *
     * 0f when nothing has been added, or when the bins somehow sum to nothing.
     */
    val tonality: Float
        get() {
            if (frameCount == 0) return 0f
            var mean = 0f
            for (b in 0 until Chromagram.CHROMA_BINS) mean += chromaSum[b]
            mean /= Chromagram.CHROMA_BINS
            if (mean <= 0f) return 0f
            var variance = 0f
            for (b in 0 until Chromagram.CHROMA_BINS) {
                val c = chromaSum[b] - mean
                variance += c * c
            }
            return sqrt(variance / Chromagram.CHROMA_BINS) / mean
        }

    /** Forget everything — called when a fresh strum lands. */
    fun reset() {
        chromaSum.fill(0f)
        bassSum.fill(0f)
        frameCount = 0
    }

    /**
     * The best [limit] chords for the evidence so far, best first. Empty if nothing has been added.
     *
     * Scoring is the template correlation plus [ChordTemplates.QUALITY_BIAS] plus a root-in-the-bass
     * bonus. That last term is this app's own: a chromagram folds every octave together, so it
     * cannot tell which note is on the bottom, and several chords in the vocabulary are genuinely
     * indistinguishable without that. Csus2 and Gsus4 are the same three pitch classes and correlate
     * *identically* — only the bass says which one a guitarist just played. The same evidence
     * quietly helps everywhere else too, since the root usually sits on the low E or A string.
     */
    fun rank(limit: Int = ChordTemplates.COUNT): List<ChordCandidate> {
        if (frameCount == 0 || limit <= 0) return emptyList()

        // Mean-centre the accumulated chroma; the sum's scale cancels in the correlation, so there
        // is no need to divide by frameCount first.
        var mean = 0f
        for (b in 0 until Chromagram.CHROMA_BINS) mean += chromaSum[b]
        mean /= Chromagram.CHROMA_BINS
        var variance = 0f
        for (b in 0 until Chromagram.CHROMA_BINS) { val c = chromaSum[b] - mean; variance += c * c }
        // A vector with no shape has no chord in it. Checked *before* the correlation below divides
        // the shape out: that division is what makes scoring scale-invariant, and it would happily
        // normalise room tone's near-flat chroma up to a confident-looking correlation, so some
        // chord always won. Answering nothing is the honest reply to noise.
        if (variance <= 0f || tonality < TONALITY_MIN) return emptyList()
        val inv = 1f / sqrt(variance)

        // Bass evidence per pitch class, as a deviation from flat: 0f when the bass register holds
        // no preference, positive for the pitch class it actually sits on.
        val bassTotal = bassSum.sum()
        val bassEvidence = FloatArray(Chromagram.CHROMA_BINS) { b ->
            if (bassTotal <= 0f) 0f
            else (bassSum[b] / bassTotal * Chromagram.CHROMA_BINS - 1f).coerceIn(-1f, MAX_BASS_EVIDENCE)
        }

        val scored = ArrayList<ChordCandidate>(ChordTemplates.COUNT)
        for (label in 0 until ChordTemplates.COUNT) {
            val tpl = ChordTemplates.TEMPLATES[label]
            var dot = 0f
            for (b in 0 until Chromagram.CHROMA_BINS) dot += (chromaSum[b] - mean) * tpl[b]
            val chord = ChordTemplates.CHORDS[label]
            val score = dot * inv +
                ChordTemplates.QUALITY_BIAS[label] +
                BASS_WEIGHT * bassEvidence[chord.root]
            scored.add(ChordCandidate(chord, score))
        }
        scored.sortByDescending { it.score }
        return if (limit >= scored.size) scored else scored.subList(0, limit).toList()
    }

    companion object {
        /**
         * Weight of the root-in-the-bass bonus, in Pearson units. Sized to break a tie between
         * chords that chroma cannot separate at all, without letting a booming low string override
         * a clear harmonic match — an inverted chord (root not in the bass) must still be able to
         * win on template correlation alone.
         */
        private const val BASS_WEIGHT = 0.08f

        /** Ceiling on how far one pitch class may dominate the bass, keeping the bonus bounded. */
        private const val MAX_BASS_EVIDENCE = 3f

        /**
         * Least [tonality] that may be called a chord. Below it, [rank] answers nothing.
         *
         * Measured, not guessed. On a Fairphone 6 in a quiet room, rain on the roof and footsteps
         * read 0.153-0.242, while chords from an acoustic 4m away on the phone's blind side read
         * 0.514-1.384 — no overlap, a factor of 2.1 between them. This sits at the geometric mean
         * of the two edges: 45% clear of the loudest noise, 32% below the weakest real chord.
         *
         * Deliberately a shape test, not a level test, so it costs nothing at the sensitivity floor
         * this app is built for — a quiet unplugged hollow-body across the room is faint but every
         * bit as peaked as a mic'd amp. Raise it only against measurements, and against the quietest
         * guitar that must still work rather than a comfortable one.
         */
        private const val TONALITY_MIN = 0.35f
    }
}
