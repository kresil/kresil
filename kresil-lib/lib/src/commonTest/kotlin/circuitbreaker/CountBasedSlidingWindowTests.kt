package circuitbreaker

import kresil.circuitbreaker.slidingwindow.CountBasedSlidingWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CountBasedSlidingWindowTests {

    @Test
    fun currentFailureRateReturnsZeroIfFewerThanMinimumThroughputCallsAreRecorded() {
        // given: a sliding window with a minimum throughput
        val minimumThroughput = 3
        val slidingWindow = CountBasedSlidingWindow(
            capacity = 1000,
            minimumThroughput = minimumThroughput
        )

        // when: minimumThroughput - 1 operations are recorded
        repeat(minimumThroughput - 1) {
            slidingWindow.recordFailure()
        }

        // then: the failure rate is 0
        assertEquals(0.0, slidingWindow.currentFailureRate())

        // when: one more operation is recorded
        slidingWindow.recordFailure()

        // then: the failure rate is calculated
        assertEquals(1.0, slidingWindow.currentFailureRate())
    }

    @Test
    fun currentFailureRateIsUpdatedWhenOperationsAreRecorded() {
        // given: a list of minimumThroughput and percentage of failure rate
        val x = listOf(10 to .1, 100 to .01, 1000 to .001, 10000 to .0001)
        for ((minimumThroughput, failurePercentage) in x) {
            // and: a sliding window with a minimum throughput
            val slidingWindow = CountBasedSlidingWindow(
                capacity = minimumThroughput,
                minimumThroughput = minimumThroughput
            )

            // when: minimumThroughput operations are recorded
            repeat(minimumThroughput) {
                slidingWindow.recordFailure()
            }

            // then: the failure rate is calculated
            assertEquals(1.0, slidingWindow.currentFailureRate())

            // when: other operations are recorded to fill the window
            repeat(minimumThroughput) {
                slidingWindow.recordSuccess()
                // then: the failure rate is updated accordingly
                assertEquals(
                    1.0 - (it + 1) * failurePercentage,
                    slidingWindow.currentFailureRate(),
                    absoluteTolerance = 1e-15 // concerns about floating point precision
                )
            }

            // and: the failure rate is back to 0
            assertEquals(0.0, slidingWindow.currentFailureRate())
        }

    }

    @Test
    fun currentFailureRateReturnsZeroWhenCleared() {
        // given: a sliding window with a minimum throughput
        val minimumThroughput = 3
        val slidingWindow = CountBasedSlidingWindow(
            capacity = 1000,
            minimumThroughput = minimumThroughput
        )

        // when: minimumThroughput operations are recorded
        repeat(minimumThroughput) {
            slidingWindow.recordFailure()
        }

        // then: the failure rate is calculated
        assertEquals(1.0, slidingWindow.currentFailureRate())

        // when: the window is cleared
        slidingWindow.clear()

        // then: the failure rate is 0
        assertEquals(0.0, slidingWindow.currentFailureRate())

        // when: minimumThroughput operations are recorded
        repeat(minimumThroughput) {
            slidingWindow.recordFailure()
        }

        // then: the failure rate is calculated again
        assertEquals(1.0, slidingWindow.currentFailureRate())
    }

    @Test
    fun mimimumThroughputMustBeGreaterThanZero() {
        // given: a minimum throughput of 0
        val minimumThroughput = 0

        // then: an exception is thrown
        val ex = assertFailsWith<IllegalArgumentException> {
            CountBasedSlidingWindow(
                capacity = 1000,
                minimumThroughput = minimumThroughput
            )
        }

        // and: the exception message is correct
        assertEquals("Minimum throughput must be greater than 0", ex.message)
    }

    @Test
    fun capacityMustBeGreaterThanZero() {
        // given: a capacity of 0
        val capacity = 0

        // then: an exception is thrown
        val ex = assertFailsWith<IllegalArgumentException> {
            CountBasedSlidingWindow(
                capacity = capacity,
                minimumThroughput = 1000
            )
        }

        // and: the exception message is correct
        assertEquals("Capacity must be greater than 0", ex.message)
    }

}
