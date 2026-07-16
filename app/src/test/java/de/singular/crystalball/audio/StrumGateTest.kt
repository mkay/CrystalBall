package de.singular.crystalball.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate is what decides whether the app hears you at all, and the first version got it wrong in
 * two ways that these tests pin down: it used a fixed absolute threshold that a quiet instrument
 * never cleared, and it looked for a sharp attack between windows that overlap 75% — where a real
 * strum's rise is spread over several windows and never looks sharp.
 */
class StrumGateTest {

    /** Feed room tone for long enough that the floor has settled on it. */
    private fun StrumGate.settle(roomRms: Float, windows: Int = 12) {
        repeat(windows) { update(roomRms) }
    }

    @Test
    fun `room tone alone never counts as a strum`() {
        val gate = StrumGate()
        repeat(50) { assertEquals(GateVerdict.QUIET, gate.update(0.004f)) }
    }

    @Test
    fun `a quiet unplugged guitar in a quiet room is heard`() {
        // The failing case from the field: a resonant hollow-body, unplugged, well under the old
        // fixed 0.01 floor — but still far above a quiet room.
        val gate = StrumGate()
        gate.settle(roomRms = 0.001f)
        assertEquals(GateVerdict.STRUM, gate.update(0.006f))
        assertEquals(GateVerdict.SOUNDING, gate.update(0.005f))
    }

    @Test
    fun `a loud amp in a noisy room is heard`() {
        val gate = StrumGate()
        gate.settle(roomRms = 0.02f)
        assertEquals(GateVerdict.STRUM, gate.update(0.3f))
        assertEquals(GateVerdict.SOUNDING, gate.update(0.25f))
    }

    @Test
    fun `a gradual rise still registers, as overlapping windows produce`() {
        // A strum smeared across four 75%-overlapping windows: no single step is a sharp jump, and
        // an edge detector comparing neighbours would miss it entirely.
        val gate = StrumGate()
        gate.settle(roomRms = 0.002f)
        val ramp = listOf(0.004f, 0.007f, 0.012f, 0.02f, 0.03f)
        val verdicts = ramp.map { gate.update(it) }
        assertTrue("the strum must be caught somewhere in the ramp", verdicts.contains(GateVerdict.STRUM))
        assertEquals(GateVerdict.SOUNDING, gate.update(0.028f))
    }

    @Test
    fun `a decaying chord does not chatter into repeated strums`() {
        val gate = StrumGate()
        gate.settle(roomRms = 0.002f)
        assertEquals(GateVerdict.STRUM, gate.update(0.2f))
        // Ring out slowly; hysteresis should hold it SOUNDING all the way down, never re-triggering.
        var level = 0.2f
        repeat(30) {
            level *= 0.9f
            val v = gate.update(level)
            assertTrue("unexpected re-trigger at rms=$level", v != GateVerdict.STRUM)
        }
    }

    @Test
    fun `strumming again over a ringing chord starts a new strum`() {
        val gate = StrumGate()
        gate.settle(roomRms = 0.002f)
        assertEquals(GateVerdict.STRUM, gate.update(0.15f))
        assertEquals(GateVerdict.SOUNDING, gate.update(0.10f))
        assertEquals(GateVerdict.SOUNDING, gate.update(0.06f))
        // Hit it again before it died away.
        assertEquals(GateVerdict.STRUM, gate.update(0.22f))
    }

    @Test
    fun `a strum during warm-up does not deafen the gate`() {
        // The player does not wait for the button: the very first windows are already loud. The
        // floor must not be measured from the guitar, or nothing would ever clear it again.
        val gate = StrumGate()
        repeat(4) { gate.update(0.3f) }
        // Chord dies, room returns, and the next strum must still register.
        repeat(20) { gate.update(0.002f) }
        assertEquals(GateVerdict.STRUM, gate.update(0.05f))
    }

    @Test
    fun `digital silence cannot trigger on a zero floor`() {
        // A zero noise floor makes any ratio of it zero too; the absolute minimum is the backstop.
        val gate = StrumGate()
        repeat(10) { assertEquals(GateVerdict.QUIET, gate.update(0f)) }
        assertEquals(GateVerdict.QUIET, gate.update(0.0005f))
        assertTrue(gate.onThreshold > 0f)
    }

    @Test
    fun `the floor follows a room that gets louder`() {
        val gate = StrumGate()
        gate.settle(roomRms = 0.001f)
        // Room noise rises (fan, traffic) and stays there; the floor should creep up to it rather
        // than treat the new room tone as a permanent chord.
        repeat(400) { gate.update(0.01f) }
        assertTrue("floor should have followed the room up", gate.noiseFloor > 0.005f)
        assertEquals(GateVerdict.QUIET, gate.update(0.01f))
    }
}
