package com.example.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the tap-sequence arbiter (P3/P2 karaoke gestures):
 * - a single tap NEVER fires a double-tap action (prevents accidental seeks),
 * - a double tap fires only after the window passes without a third tap,
 * - a triple tap fires immediately and cancels the pending double.
 */
class TapSequenceArbiterTest {

    private val window = 300L

    @Test
    fun `single tap schedules nothing and never fires`() {
        val arbiter = TapSequenceArbiter(window)
        val decision = arbiter.registerTap(1_000L)
        assertTrue(decision is TapSequenceArbiter.Decision.None)
        assertFalse(arbiter.expire(1_000L))
        assertFalse(arbiter.expire(5_000L))
    }

    @Test
    fun `double tap schedules a double action with correct deadline`() {
        val arbiter = TapSequenceArbiter(window)
        assertTrue(arbiter.registerTap(1_000L) is TapSequenceArbiter.Decision.None)

        val decision = arbiter.registerTap(1_100L)
        assertTrue(decision is TapSequenceArbiter.Decision.ScheduleDouble)
        assertEquals(1_100L + window, (decision as TapSequenceArbiter.Decision.ScheduleDouble).deadlineMillis)

        // Not yet due
        assertFalse(arbiter.expire(1_250L))
        // Due exactly at the deadline, and only once
        assertTrue(arbiter.expire(1_400L))
        assertFalse(arbiter.expire(1_401L))
    }

    @Test
    fun `triple tap fires immediately and cancels pending double`() {
        val arbiter = TapSequenceArbiter(window)
        arbiter.registerTap(2_000L)
        val second = arbiter.registerTap(2_100L)
        assertTrue(second is TapSequenceArbiter.Decision.ScheduleDouble)

        val third = arbiter.registerTap(2_200L)
        assertTrue(third is TapSequenceArbiter.Decision.FireTriple)

        // The scheduled double must never fire after a triple
        assertFalse(arbiter.expire(2_500L))
        assertFalse(arbiter.expire(9_999L))
    }

    @Test
    fun `taps outside the window restart the sequence`() {
        val arbiter = TapSequenceArbiter(window)
        assertTrue(arbiter.registerTap(0L) is TapSequenceArbiter.Decision.None)
        // Second tap beyond window: treated as a fresh first tap
        val afterGap = arbiter.registerTap(1_000L)
        assertTrue(afterGap is TapSequenceArbiter.Decision.None)
        assertFalse(arbiter.expire(2_000L))
    }

    @Test
    fun `fourth tap after triple starts a fresh sequence`() {
        val arbiter = TapSequenceArbiter(window)
        arbiter.registerTap(0L)
        arbiter.registerTap(100L)
        assertTrue(arbiter.registerTap(200L) is TapSequenceArbiter.Decision.FireTriple)

        // Fresh single tap — no pending double from the previous sequence
        assertTrue(arbiter.registerTap(300L) is TapSequenceArbiter.Decision.None)
        assertFalse(arbiter.expire(10_000L))
    }

    @Test
    fun `reset clears pending state`() {
        val arbiter = TapSequenceArbiter(window)
        arbiter.registerTap(0L)
        arbiter.registerTap(100L)
        arbiter.reset()
        assertFalse(arbiter.expire(10_000L))
        assertTrue(arbiter.registerTap(200L) is TapSequenceArbiter.Decision.None)
    }
}
