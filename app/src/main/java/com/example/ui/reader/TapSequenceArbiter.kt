package com.example.ui.reader

/**
 * Pure decision logic for word tap sequences. No Android dependencies.
 *
 * Rules (P2 karaoke gestures):
 *  - Single tap: NO action (never seeks — prevents accidental playback jumps).
 *  - Double tap: fires only after [windowMillis] passes with no third tap.
 *  - Triple tap: fires immediately and cancels any pending double-tap.
 *
 * The caller supplies timestamps so the class is fully unit-testable.
 */
class TapSequenceArbiter(
    private val windowMillis: Long = 300L
) {
    sealed class Decision {
        /** Nothing to do for this tap (waiting for potential follow-up taps). */
        object None : Decision()

        /** Schedule a double-tap action at [deadlineMillis]; cancel if a third tap arrives. */
        data class ScheduleDouble(val deadlineMillis: Long) : Decision()

        /** A third tap arrived inside the window — fire the triple-tap action now. */
        object FireTriple : Decision()
    }

    private var tapCount = 0
    private var lastTapAt = Long.MIN_VALUE / 2
    private var pendingDoubleDeadline: Long? = null

    /**
     * Register a completed tap at [nowMillis].
     * Returns what the caller should do immediately.
     */
    fun registerTap(nowMillis: Long): Decision {
        tapCount = if (tapCount > 0 && nowMillis - lastTapAt <= windowMillis) tapCount + 1 else 1
        lastTapAt = nowMillis
        return when {
            tapCount >= 3 -> {
                pendingDoubleDeadline = null
                tapCount = 0
                Decision.FireTriple
            }
            tapCount == 2 -> {
                val deadline = nowMillis + windowMillis
                pendingDoubleDeadline = deadline
                Decision.ScheduleDouble(deadline)
            }
            else -> Decision.None
        }
    }

    /**
     * Call when a scheduled double-tap deadline may have been reached.
     * Returns true exactly once, only if no third tap cancelled the sequence.
     */
    fun expire(nowMillis: Long): Boolean {
        val deadline = pendingDoubleDeadline ?: return false
        if (nowMillis >= deadline) {
            pendingDoubleDeadline = null
            tapCount = 0
            return true
        }
        return false
    }

    fun reset() {
        tapCount = 0
        lastTapAt = Long.MIN_VALUE / 2
        pendingDoubleDeadline = null
    }
}
