// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What the microphone pass reports back to the UI. */
sealed interface ListenEvent {
    /** Input level, 0f..1f, for a "we can hear you" meter. Emitted continuously while listening. */
    data class Level(val rms: Float, val heardStrum: Boolean) : ListenEvent

    /** A chord was read. [candidates] is best-first and always non-empty. */
    data class Detected(val candidates: List<ChordCandidate>) : ListenEvent

    /** Listening ended without ever hearing anything loud enough to analyse. */
    data object Silence : ListenEvent
}

/**
 * Listens on the microphone and reports the chord it hears.
 *
 * The flow is: let [StrumGate] say when the guitar is sounding, accumulate chroma for as long as it
 * is, and stop as soon as the ranking has settled — a clean open chord resolves in roughly a third
 * of a second, while a messy one keeps collecting evidence up to [MAX_CHORD_FRAMES]. Strumming
 * again restarts the accumulation, so a bad take needs no button press. If nothing is heard at all
 * within [LISTEN_TIMEOUT_MS], it gives up with [ListenEvent.Silence] — though a strum arriving just
 * before that is given [STRUM_GRACE_MS] to finish becoming an answer, bounded by [MAX_GRACE_MS] so
 * the pass still ends on its own in a room that keeps tripping the gate.
 *
 * The attack of a strum is deliberately skipped: the pick hitting the strings is a broadband click
 * with no stable pitch content, and folding it into the chroma only blurs the answer.
 *
 * Collect this on a background dispatcher (it already moves itself to [Dispatchers.Default]);
 * cancelling the collecting coroutine stops the recorder and releases the microphone.
 */
class ChordListener {

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun listen(): Flow<ListenEvent> = flow {
        val record = openRecorder() ?: run {
            emit(ListenEvent.Silence)
            return@flow
        }
        try {
            record.startRecording()
            analyse(record)
        } finally {
            // stop() throws if the recorder never initialised; release() is what must always run.
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Build an [AudioRecord] on the plain microphone.
     *
     * Not the UNPROCESSED source, despite it being the spectrally honest one: it also ships with
     * markedly lower gain on many devices, and a quiet source — an unplugged hollow-body a metre
     * away — can end up too faint to gate on at all. The processing on MIC is mostly automatic gain
     * control, which costs us little here because [Chromagram] normalises every frame anyway, so
     * level changes wash out before the templates ever see them. Android exposes no way to set the
     * input gain directly, so picking the louder source is the only volume control we have.
     */
    @SuppressLint("MissingPermission") // guarded by @RequiresPermission on listen()
    private fun openRecorder(): AudioRecord? {
        val source = MediaRecorder.AudioSource.MIC

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return null
        // Room for several analysis windows, so a scheduling hiccup can't drop audio mid-strum.
        val bufferBytes = max(minBuffer, Chromagram.FFT_SIZE * BYTES_PER_SAMPLE * 4)

        val record = runCatching {
            AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufferBytes)
        }.getOrNull() ?: return null

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        return record
    }

    /** The capture + decision loop. Runs until a chord is emitted, the timeout hits, or cancellation. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<ListenEvent>.analyse(record: AudioRecord) {
        val chromagram = Chromagram(SAMPLE_RATE)
        val recognizer = ChordRecognizer()
        val gate = StrumGate()

        // A sliding window of the most recent FFT_SIZE samples, advanced HOP samples at a time.
        val window = FloatArray(Chromagram.FFT_SIZE)
        val incoming = ShortArray(Chromagram.HOP)

        var samplesSeen = 0L
        var framesSounding = 0 // windows since the current strum started; 0 = nothing heard yet
        var stableChord: Chord? = null
        var stableFrames = 0

        val start = System.nanoTime()
        var deadline = start + LISTEN_TIMEOUT_MS * 1_000_000L
        // However many strums arrive late, the pass ends here. Without a ceiling, anything that
        // repeatedly trips the gate would renew the deadline forever and a run would never end on
        // its own — the phantom-chord problem again, wearing a different hat.
        val latestDeadline = start + (LISTEN_TIMEOUT_MS + MAX_GRACE_MS) * 1_000_000L

        while (System.nanoTime() < deadline) {
            coroutineContext.ensureActive()

            if (!readFully(record, incoming)) return

            // Slide the window left by one hop and append the new samples, scaled to -1f..1f.
            System.arraycopy(window, Chromagram.HOP, window, 0, Chromagram.FFT_SIZE - Chromagram.HOP)
            val tail = Chromagram.FFT_SIZE - Chromagram.HOP
            for (i in incoming.indices) window[tail + i] = incoming[i] / 32768f
            samplesSeen += Chromagram.HOP
            // Until the window is full it is part silence, which would skew both level and chroma.
            if (samplesSeen < Chromagram.FFT_SIZE) continue

            val rms = rms(window)
            when (gate.update(rms)) {
                GateVerdict.QUIET -> {
                    emit(ListenEvent.Level(rms, heardStrum = false))
                    continue
                }
                GateVerdict.STRUM -> {
                    // A fresh strum supersedes whatever we had — start the evidence over.
                    recognizer.reset()
                    framesSounding = 1
                    stableChord = null
                    stableFrames = 0
                    // ...and evidence started over needs time to become an answer: the attack is
                    // skipped and MIN_CHORD_FRAMES must be met before any chord may be reported,
                    // some 420 ms in all. A strum landing inside that of the deadline would
                    // otherwise be cut off mid-collection and reported as silence — which a capture
                    // run reads as the guitar being put down, ending it on a chord you just played.
                    deadline = min(
                        latestDeadline,
                        max(deadline, System.nanoTime() + STRUM_GRACE_MS * 1_000_000L),
                    )
                }
                GateVerdict.SOUNDING -> framesSounding++
            }
            emit(ListenEvent.Level(rms, heardStrum = true))

            // Let the pick transient pass before believing the spectrum.
            if (framesSounding <= ATTACK_SKIP_FRAMES) continue

            val frame = chromagram.frame(window, 0) ?: continue
            recognizer.add(frame)
            if (recognizer.frameCount < MIN_CHORD_FRAMES) continue

            val top = recognizer.rank(2)
            if (top.size < 2) continue
            if (top[0].chord == stableChord) stableFrames++ else {
                stableChord = top[0].chord
                stableFrames = 1
            }
            val settled = stableFrames >= STABLE_FRAMES && top[0].score - top[1].score >= MARGIN
            if (settled || recognizer.frameCount >= MAX_CHORD_FRAMES) {
                emit(ListenEvent.Detected(recognizer.rank(CANDIDATE_COUNT)))
                return
            }
        }

        // Timed out. Report whatever evidence we have rather than throwing it away — unless there
        // is no chord in it, which is what an empty ranking means: the recogniser refuses noise
        // below its tonality floor. That is the difference between a run that ends when you put the
        // guitar down and one that fills itself with phantom chords from the room.
        val candidates = recognizer.rank(CANDIDATE_COUNT)
        emit(
            if (recognizer.frameCount >= MIN_CHORD_FRAMES && candidates.isNotEmpty())
                ListenEvent.Detected(candidates)
            else
                ListenEvent.Silence
        )
    }

    /** Fill [out] completely. Returns false if the recorder errored or closed mid-read. */
    private fun readFully(record: AudioRecord, out: ShortArray): Boolean {
        var offset = 0
        while (offset < out.size) {
            val n = record.read(out, offset, out.size - offset)
            if (n <= 0) return false
            offset += n
        }
        return true
    }

    private fun rms(samples: FloatArray): Float {
        var sum = 0.0
        for (s in samples) sum += (s * s).toDouble()
        return sqrt(sum / samples.size).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2

        /** Give up if no strum is heard at all within this long. */
        const val LISTEN_TIMEOUT_MS = 8000L

        /**
         * Time a strum is given to become an answer when it lands near the deadline. Covers the
         * ~420 ms before any chord may be reported, with room to settle properly rather than
         * scraping in on the timeout path's minimum evidence.
         */
        const val STRUM_GRACE_MS = 700L

        /** Most a pass may overrun [LISTEN_TIMEOUT_MS], however many strums arrive late. */
        const val MAX_GRACE_MS = 1500L

        /** Windows to discard after a strum while the broadband pick transient decays (~140 ms). */
        const val ATTACK_SKIP_FRAMES = 3

        /** Minimum evidence before any answer is offered (~280 ms of chord). */
        const val MIN_CHORD_FRAMES = 6

        /** Evidence at which we answer regardless of confidence (~1.4 s of chord). */
        const val MAX_CHORD_FRAMES = 30

        /** Consecutive frames the leader must hold to count as settled. */
        const val STABLE_FRAMES = 5

        /** …and by at least this much correlation over the runner-up. */
        const val MARGIN = 0.03f

        /** Enough for the best fit plus both alternative rows the UI shows. */
        const val CANDIDATE_COUNT = 8
    }
}
