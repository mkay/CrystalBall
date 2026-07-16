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
        // A flat vector has no shape to match — no chord can be read out of it.
        if (variance <= 0f) return emptyList()
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
    }
}
