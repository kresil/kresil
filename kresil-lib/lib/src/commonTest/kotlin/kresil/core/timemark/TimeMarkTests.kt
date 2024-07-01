package kresil.core.timemark

import kotlinx.coroutines.test.runTest
import kresil.extensions.delayWithRealTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class TimeMarkTests {

    @Test
    fun getCurrentTimeMarkShouldReturnCurrentTimeMark() = runTest {
        // given: the current time mark
        val timeMark = getCurrentTimeMark()

        // then: the elapsed time should be non-negative
        assertTrue(timeMark.elapsedNow() >= Duration.ZERO, "Elapsed time should be non-negative")
    }

    @Test
    fun hasExceededDurationShouldReturnCorrectResult() = runTest {
        // given: the current time mark
        val timeMark = TimeSource.Monotonic.markNow()

        // and: a duration of 1 second
        val duration = 1.seconds

        // then: immediately after marking, the duration should not have been exceeded
        assertFalse(hasExceededDuration(timeMark, duration), "Should not have exceeded duration immediately after marking")

        // when: waiting for the duration amount
        delayWithRealTime(duration)

        // then: the duration should have been exceeded
        assertTrue(hasExceededDuration(timeMark, duration), "Should have exceeded duration after waiting")
    }

    @Test
    fun getRemainingDurationShouldReturnCorrectRemainingDuration() = runTest {
        // given: the current time mark
        val timeMark = getCurrentTimeMark()

        // and: a duration of 1 second
        val duration = 1.seconds

        // when: immediately after marking, get the remaining duration
        val remainingDurationImmediate = getRemainingDuration(timeMark, duration)

        // then: the remaining duration should be close to the initial duration
        assertTrue(
            remainingDurationImmediate in duration - 0.1.seconds..duration + 0.1.seconds,
            "Remaining duration should be close to the initial duration immediately after marking"
        )

        // when: waiting for the duration amount
        delayWithRealTime(duration)

        // then: the remaining duration should be zero
        val remainingDurationAfterDelay = getRemainingDuration(timeMark, duration)
        assertEquals(Duration.ZERO, remainingDurationAfterDelay, "Remaining duration should be zero after exceeding the duration")
    }
}
