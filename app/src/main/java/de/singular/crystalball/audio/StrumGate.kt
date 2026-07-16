package de.singular.crystalball.audio

import kotlin.math.max
import kotlin.math.min

/** What the gate makes of one analysis window. */
enum class GateVerdict {
    /** Room tone. Ignore it. */
    QUIET,

    /** The guitar is sounding, continuing what the previous window heard. */
    SOUNDING,

    /** A new strum starts here — evidence collected so far belongs to the previous one. */
    STRUM,
}

/**
 * Decides, window by window, whether the guitar is sounding and where a new strum begins.
 *
 * It works from an **adaptive noise floor** rather than a fixed level, because there is no absolute
 * threshold that is right for both a mic'd amp and an unplugged hollow-body across the room: the
 * two differ by tens of dB, and any constant that hears the quiet one would trigger on the loud
 * one's room tone. The floor tracks the quietest thing recently heard, and "sounding" means
 * standing a good margin above *that*.
 *
 * It also does not look for a sharp attack. Analysis windows are long (185 ms at 44.1 kHz) and
 * overlap by 75%, so a strum's onset is smeared across four of them and adjacent windows rarely
 * differ by much — an edge detector comparing neighbours misses real strums routinely. Crossing the
 * threshold is the signal instead, with hysteresis so a decaying chord does not chatter, plus a
 * jump test that catches a re-strum before the previous chord has died away.
 *
 * Pure logic, no audio dependencies — see `StrumGateTest`.
 */
class StrumGate {

    /** Quietest level recently seen: the room, as measured rather than assumed. */
    var noiseFloor = Float.MAX_VALUE
        private set

    private var sounding = false
    private var previousRms = 0f

    /** Level above which a window counts as the guitar sounding. */
    val onThreshold: Float
        get() = max(usableFloor * ON_RATIO, ABS_MIN_RMS)

    /** Level below which it counts as finished — lower than [onThreshold], so decay doesn't chatter. */
    private val offThreshold: Float
        get() = max(usableFloor * OFF_RATIO, ABS_MIN_RMS * OFF_RATIO / ON_RATIO)

    /**
     * The floor, clamped. The ceiling matters: if the very first windows already contain a strum
     * (the player does not wait for the button), the floor would otherwise be measured *from* the
     * guitar and nothing would ever clear the threshold again.
     */
    private val usableFloor: Float
        get() = min(noiseFloor, NOISE_FLOOR_MAX)

    /** Feed one window's RMS; get back what it means. */
    fun update(rms: Float): GateVerdict {
        trackFloor(rms)

        val wasSounding = sounding
        sounding = if (sounding) rms > offThreshold else rms > onThreshold

        val verdict = when {
            !sounding -> GateVerdict.QUIET
            // Crossed up from silence: unambiguously a new strum.
            !wasSounding -> GateVerdict.STRUM
            // Already ringing, and the level jumps: strummed again over the decaying chord.
            rms > previousRms * RESTRUM_RISE && rms > onThreshold * RESTRUM_FLOOR_FACTOR ->
                GateVerdict.STRUM
            else -> GateVerdict.SOUNDING
        }
        previousRms = rms
        return verdict
    }

    /**
     * Track the floor down fast and up slowly. Falling straight to any new minimum settles on the
     * room in a fraction of a second; creeping up lets it follow a room that genuinely gets louder
     * without being dragged up by a chord that is merely ringing.
     */
    private fun trackFloor(rms: Float) {
        noiseFloor = if (rms < noiseFloor) rms else noiseFloor * FLOOR_CREEP
    }

    private companion object {
        /**
         * The guitar must stand this far above the room to register. Low enough for a quiet
         * unplugged instrument, high enough that room tone alone cannot reach it.
         */
        const val ON_RATIO = 2.2f

        /** Release threshold, as a multiple of the floor. Hysteresis: below [ON_RATIO]. */
        const val OFF_RATIO = 1.4f

        /**
         * Absolute floor under everything, about -56 dBFS. Guards the degenerate case of a
         * digitally silent input, where the noise floor is zero and any ratio of it is zero too.
         * Deliberately far below the old fixed 0.01 (-40 dBFS), which an unplugged hollow-body
         * never cleared.
         */
        const val ABS_MIN_RMS = 0.0015f

        /** Ceiling on the measured floor, so a strum during warm-up cannot deafen the gate. */
        const val NOISE_FLOOR_MAX = 0.02f

        /** Per-window upward drift of the floor: ~1.6%/window, so a held chord barely moves it. */
        const val FLOOR_CREEP = 1.016f

        /** A re-strum over a ringing chord: this much louder than the window before. */
        const val RESTRUM_RISE = 1.8f

        /** …and clearly above the on-threshold, so decay wobble cannot fake it. */
        const val RESTRUM_FLOOR_FACTOR = 1.5f
    }
}
