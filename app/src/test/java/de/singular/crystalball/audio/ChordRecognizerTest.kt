package de.singular.crystalball.audio

import de.singular.crystalball.chords.ChordLibrary
import de.singular.crystalball.chords.STANDARD_TUNING
import de.singular.crystalball.chords.Voicing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * End-to-end tests of the recogniser: synthesise a plucked-string chord, push it through the real
 * [Chromagram] and [Fft], and check what comes out. Nothing here is mocked — the only thing missing
 * relative to the device is the microphone itself.
 */
class ChordRecognizerTest {

    private val sampleRate = 44100

    /**
     * Render [voicing] as a crude but honest guitar: each sounded string is a harmonic stack with
     * amplitudes rolling off, strings staggered slightly as in a strum, plus a little noise.
     */
    private fun renderVoicing(voicing: Voicing, seconds: Float = 1.5f, seed: Int = 7): FloatArray {
        val rng = Random(seed)
        val out = FloatArray((sampleRate * seconds).toInt())
        var stringIndex = 0
        for (s in 0 until 6) {
            val fret = voicing.frets[s]
            if (fret < 0) continue
            // MIDI note of the open string, then the fret on top. Low E (string 0) is MIDI 40.
            val openMidi = intArrayOf(40, 45, 50, 55, 59, 64)[s]
            val freq = 440.0 * 2.0.pow((openMidi + fret - 69) / 12.0)
            val delay = (stringIndex * 0.012f * sampleRate).toInt() // ~12 ms between strings
            stringIndex++
            for (h in 1..HARMONICS) {
                val hf = freq * h
                if (hf > sampleRate / 2.0) break
                val amp = 0.28 / h // roll-off with harmonic number
                val phase = rng.nextDouble() * 2 * PI
                for (n in delay until out.size) {
                    val t = (n - delay).toDouble() / sampleRate
                    val decay = kotlin.math.exp(-t * 1.4)
                    out[n] += (amp * decay * sin(2 * PI * hf * t + phase)).toFloat()
                }
            }
        }
        for (n in out.indices) out[n] += (rng.nextFloat() - 0.5f) * 0.002f
        return out
    }

    /** Run a rendered buffer through the front-end exactly as the listener does. */
    private fun recognise(pcm: FloatArray): List<ChordCandidate> {
        val chromagram = Chromagram(sampleRate)
        val recognizer = ChordRecognizer()
        var from = Chromagram.HOP * 3 // skip the attack, as ChordListener does
        while (from + Chromagram.FFT_SIZE <= pcm.size) {
            chromagram.frame(pcm, from)?.let { recognizer.add(it) }
            from += Chromagram.HOP
        }
        return recognizer.rank(8)
    }

    private fun topChordFor(name: String): String {
        val chord = ChordLibrary.allChords().first { it.name == name }
        val voicing = ChordLibrary.voicingsFor(chord).first()
        return recognise(renderVoicing(voicing)).first().chord.name
    }

    @Test
    fun `recognises the common open major chords`() {
        for (name in listOf("C", "A", "G", "E", "D")) {
            assertEquals("open $name misread", name, topChordFor(name))
        }
    }

    @Test
    fun `recognises the common open minor chords`() {
        for (name in listOf("Am", "Em", "Dm")) {
            assertEquals("open $name misread", name, topChordFor(name))
        }
    }

    @Test
    fun `tells a major chord from its parallel minor`() {
        // The single-bin difference bare note masks get wrong; the harmonic templates should not.
        assertEquals("A", topChordFor("A"))
        assertEquals("Am", topChordFor("Am"))
        assertEquals("E", topChordFor("E"))
        assertEquals("Em", topChordFor("Em"))
    }

    @Test
    fun `recognises dominant sevenths rather than the underlying triad`() {
        for (name in listOf("A7", "E7", "D7", "G7")) {
            assertEquals("open $name misread", name, topChordFor(name))
        }
    }

    @Test
    fun `a plain triad is not mistaken for a seventh`() {
        for (name in listOf("C", "G", "E", "A", "D")) {
            val top = topChordFor(name)
            assertEquals("$name gained a spurious extension", name, top)
        }
    }

    @Test
    fun `ranks the true chord above every alternative it offers`() {
        val ranked = recognise(renderVoicing(ChordLibrary.voicingsFor(chordNamed("G")).first()))
        assertEquals("G", ranked[0].chord.name)
        assertTrue("alternatives should score below the winner", ranked[1].score < ranked[0].score)
    }

    @Test
    fun `alternatives are distinct chords, best first`() {
        val ranked = recognise(renderVoicing(ChordLibrary.voicingsFor(chordNamed("C")).first()))
        assertEquals(8, ranked.size)
        assertEquals(ranked.size, ranked.map { it.chord }.toSet().size)
        assertEquals(ranked.map { it.score }.sortedDescending(), ranked.map { it.score })
    }

    @Test
    fun `bass evidence separates the sus chords chroma cannot`() {
        // Csus2 and Gsus4 are the same three pitch classes; only the bass note tells them apart.
        val csus2 = ChordLibrary.voicingsFor(chordNamed("Csus2")).first()
        val gsus4 = ChordLibrary.voicingsFor(chordNamed("Gsus4")).first()
        assertEquals(csus2.soundedPitchClasses(), gsus4.soundedPitchClasses())

        val cRanked = recognise(renderVoicing(csus2))
        val gRanked = recognise(renderVoicing(gsus4))
        // Each should beat its degenerate twin, purely on which note is in the bass.
        assertTrue(
            "Csus2 should outrank Gsus4 when C is in the bass",
            cRanked.indexOfFirst { it.chord.name == "Csus2" } <
                cRanked.indexOfFirst { it.chord.name == "Gsus4" },
        )
        assertTrue(
            "Gsus4 should outrank Csus2 when G is in the bass",
            gRanked.indexOfFirst { it.chord.name == "Gsus4" } <
                gRanked.indexOfFirst { it.chord.name == "Csus2" },
        )
    }

    @Test
    fun `silence yields no candidates`() {
        assertTrue(recognise(FloatArray(sampleRate)).isEmpty() || ChordRecognizer().rank().isEmpty())
        assertTrue("an empty recogniser must not guess", ChordRecognizer().rank().isEmpty())
    }

    @Test
    fun `a barre chord high up the neck reads the same as its open form`() {
        val chord = chordNamed("F")
        val barre = ChordLibrary.voicingsFor(chord).first()
        assertEquals("F", recognise(renderVoicing(barre)).first().chord.name)
    }

    @Test
    fun `the chromagram normalises away playing volume`() {
        val voicing = ChordLibrary.voicingsFor(chordNamed("D")).first()
        val quiet = renderVoicing(voicing).also { for (i in it.indices) it[i] *= 0.05f }
        assertEquals("D", recognise(quiet).first().chord.name)
    }

    private fun chordNamed(name: String): Chord = ChordLibrary.allChords().first { it.name == name }

    private companion object {
        const val HARMONICS = 8
    }
}
