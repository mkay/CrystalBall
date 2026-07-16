package de.singular.crystalball.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One analysis frame: a 12-bin pitch-class vector, a second fold weighted toward the bass, and the
 * frame's total in-band energy.
 *
 * [chroma] and [bass] are L1-normalised (each sums to 1) when [energy] is above zero, so frames
 * classify on the same footing regardless of how hard the strum was. [energy] keeps the *un*
 * normalised level, which is what onset and silence decisions read.
 */
class ChromaFrame(val chroma: FloatArray, val bass: FloatArray, val energy: Float)

/**
 * STFT → chroma front-end, lifted from RubberRing's `ChordDetector` and reshaped for live audio.
 *
 * Two things differ from the original. It is **incremental**: RubberRing decoded a whole file and
 * built the entire chromagram in one pass, whereas here frames arrive from the microphone, so this
 * holds the per-sample-rate precomputation and turns one [FFT_SIZE] window into one [ChromaFrame]
 * on demand. And it additionally folds a **bass chroma** over [BASS_MAX_FREQ_HZ] and below, which
 * [ChordRecognizer] needs to identify the root — see there for why.
 *
 * The geometry and band limits are RubberRing's, unchanged, and its reasoning still holds: at
 * 44.1 kHz an [FFT_SIZE] of 8192 gives ~5.4 Hz bins, finer than a semitone down to ~65 Hz (the
 * bottom of the band), where a 2048 FFT would smear low chord tones across neighbouring pitch
 * classes. Below [MIN_FREQ_HZ] is rumble with little pitch information; above [MAX_FREQ_HZ] is
 * mostly harmonics that just smear the fold.
 *
 * Pure CPU work — call it off the main thread.
 */
class Chromagram(private val sampleRate: Int) {

    private val bins = FFT_SIZE / 2 + 1

    /**
     * Pitch class of every FFT bin, or -1 for bins outside the musical band. Depends only on the
     * sample rate, so it is computed once per instance rather than per frame.
     */
    private val binPitchClass = IntArray(bins) { -1 }

    /**
     * Weight of each bin in the bass fold, 0f for bins that do not take part.
     *
     * Falls off as 1/f², which is what makes the fold report the *lowest* note rather than merely a
     * low-register average. A flat weighting over the bass band fails at exactly the job the fold
     * exists for: in a Gsus4 the band holds G, C and D at comparable strength, so the aggregate
     * cannot say which is on the bottom. Penalising by the square of frequency lets a string's
     * fundamental outweigh both its own harmonics and every note above it, so the sum lands on the
     * bass note.
     */
    private val binBassWeight = FloatArray(bins)

    private val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * PI * it / (FFT_SIZE - 1)).toFloat() }
    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)

    init {
        for (k in 1 until bins) {
            val freq = k.toFloat() * sampleRate / FFT_SIZE
            if (freq < MIN_FREQ_HZ || freq > MAX_FREQ_HZ) continue
            // MIDI note number; 69 = A4 = 440 Hz. Pitch class = note mod 12, with 69 mod 12 = 9 = A,
            // so index 0 falls on C, matching ROOT_NAMES.
            val midi = 69.0 + 12.0 * (ln((freq / 440f).toDouble()) / ln(2.0))
            binPitchClass[k] = ((midi.roundToInt() % 12) + 12) % 12
            if (freq <= BASS_MAX_FREQ_HZ) {
                val ratio = MIN_FREQ_HZ / freq
                binBassWeight[k] = ratio * ratio
            }
        }
    }

    /**
     * Analyse the [FFT_SIZE] samples of [mono] starting at [from], as one frame. [mono] is expected
     * to be mixed to a single channel and roughly in -1f..1f. Returns null if the window runs past
     * the end of the buffer.
     */
    fun frame(mono: FloatArray, from: Int): ChromaFrame? {
        if (from < 0 || from + FFT_SIZE > mono.size) return null

        for (n in 0 until FFT_SIZE) {
            re[n] = mono[from + n] * window[n]
            im[n] = 0f
        }
        Fft.transform(re, im)

        val chroma = FloatArray(CHROMA_BINS)
        val bass = FloatArray(CHROMA_BINS)
        var energy = 0f
        var bassEnergy = 0f
        for (k in 1 until bins) {
            val pc = binPitchClass[k]
            if (pc < 0) continue
            val mag = sqrt(re[k] * re[k] + im[k] * im[k])
            chroma[pc] += mag
            energy += mag
            val bassWeight = binBassWeight[k]
            if (bassWeight > 0f) {
                bass[pc] += mag * bassWeight
                bassEnergy += mag * bassWeight
            }
        }
        // Normalise so loud and soft strums classify identically. Energy is reported unnormalised.
        if (energy > 0f) for (b in 0 until CHROMA_BINS) chroma[b] /= energy
        if (bassEnergy > 0f) for (b in 0 until CHROMA_BINS) bass[b] /= bassEnergy
        return ChromaFrame(chroma, bass, energy)
    }

    companion object {
        const val FFT_SIZE = 8192
        const val HOP = 2048
        const val CHROMA_BINS = 12

        private const val MIN_FREQ_HZ = 65f
        private const val MAX_FREQ_HZ = 2000f

        /**
         * Ceiling of the bass fold — everything above this is excluded outright, on top of the
         * 1/f² weighting. A guitar's open low E is ~82 Hz and the root of a typical voicing sits
         * on the E or A string, so this spans the plausible bass notes and stops before the treble
         * strings that carry the chord's upper structure.
         */
        private const val BASS_MAX_FREQ_HZ = 350f
    }
}
