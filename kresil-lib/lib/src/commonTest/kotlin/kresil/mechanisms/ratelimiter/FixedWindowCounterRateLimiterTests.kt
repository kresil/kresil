package kresil.mechanisms.ratelimiter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kresil.core.timemark.getCurrentTimeMark
import kresil.extensions.delayWithRealTime
import kresil.extensions.measureWithRealTime
import kresil.extensions.randomTo
import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.*
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExperimentalCoroutinesApi
class FixedWindowCounterRateLimiterTests {

    private val semaphoreState = object : SemaphoreState() {

        override var permitsInUse: Int = 0
            private set

        override var replenishmentTimeMark: ComparableTimeMark = getCurrentTimeMark()
            private set

        override fun setPermits(updateFunction: (Int) -> Int) {
            permitsInUse = updateFunction(permitsInUse)
        }

        override fun setReplenishmentTimeMark(value: ComparableTimeMark) {
            replenishmentTimeMark = value
        }

        override fun close() {
            // TODO: implement close
        }

    }

    @Test
    fun shouldGrantPermitsWhenAvailable() = runTest {
        // given: a rate limiter with n-pertmis and no queue
        val permits = 1000
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(permits, INFINITE, 0))
                baseTimeoutDuration = INFINITE
                onRejected { throw it }
            },
            semaphoreState
        )

        repeat(permits) {

            // when: acquiring permits within the limit
            rateLimiter.acquire(1, timeout = INFINITE)

            // then: the permits should be granted
            assertEquals(it + 1, semaphoreState.permitsInUse)
        }

        // when: acquiring more permits outside the limit
        // then: an error message should be expected since there are no permits available and the queue length is 0
        assertFailsWith<RateLimiterRejectedException> {
            rateLimiter.acquire(1, timeout = 3.seconds)
        }

        // and: the permits should not have been granted
        assertEquals(permits, semaphoreState.permitsInUse)
    }

    @Test
    fun shouldRejectRequestWhenQueueIsFull() = runTest {
        // given: a rate limiter with 1-permit and queue length of 1
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(1, INFINITE, 1))
                baseTimeoutDuration = INFINITE
                onRejected { throw it }
            },
            semaphoreState
        )

        // when: acquiring permits within the limit
        rateLimiter.acquire(1, timeout = INFINITE)

        // then: the permits should be granted
        assertEquals(1, semaphoreState.permitsInUse)

        // when: acquiring 1 more permit outside the limit
        val request = launch {
            // then: it should go to the queue
            withContext(Dispatchers.Default) { // to disable virtual time skipping
                rateLimiter.acquire(1, timeout = INFINITE)
            }
        }

        // and: the permits should not have been granted
        delayWithRealTime(3.seconds)
        assertEquals(1, semaphoreState.permitsInUse)
        assertTrue(request.isActive)

        // when: acquiring 1 more permit outside the limit
        assertFailsWith<RateLimiterRejectedException> {
            // then: it should be rejected as the queue is full
            rateLimiter.acquire(1, timeout = INFINITE)
        }

        // and: the permits should not have been granted
        assertEquals(1, semaphoreState.permitsInUse)
        request.cancelAndJoin()
    }

    @Test
    fun shouldResumePendingRequestsWhenPermitsAreReleased() = runTest {
        // given: a rate limiter with 1-permit and queue length of 1
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(1, INFINITE, 1))
                baseTimeoutDuration = INFINITE
                onRejected { throw it }
            },
            semaphoreState
        )

        // when: acquiring permits within the limit
        rateLimiter.acquire(1, timeout = INFINITE)

        // then: the permits should be granted
        assertEquals(1, semaphoreState.permitsInUse)

        // when: acquiring 1 more permit outside the limit
        val request = launch {
            // then: it should go to the queue
            withContext(Dispatchers.Default) { // to disable virtual time skipping
                rateLimiter.acquire(1, timeout = INFINITE)
            }
        }

        // and: the permits should not have been granted
        delayWithRealTime(3.seconds)
        assertEquals(1, semaphoreState.permitsInUse)
        assertTrue(request.isActive)

        // when: acquiring 1 more permit outside the limit
        assertFailsWith<RateLimiterRejectedException> {
            // then: it should be rejected as the queue is full
            rateLimiter.acquire(1, timeout = INFINITE)
        }

        // when: releasing the permit
        rateLimiter.release(1)

        // then: the permits should be granted
        assertEquals(1, semaphoreState.permitsInUse)

        // and: the request should be granted
        delayWithRealTime(1.seconds)
        assertFalse(request.isActive)
    }

    @Test
    fun shouldReplenishPermitsAfterAPeriodIsOver() = runTest {
        // given: a rate limiter with 1-permit, no queue, and replenishment period of 1 second
        val replenishmentPeriod = 1.seconds
        val permits = 1000
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(permits, replenishmentPeriod, 1))
                baseTimeoutDuration = INFINITE
                onRejected { throw it }
            },
            semaphoreState
        )

        // when: acquiring permits within the limit
        rateLimiter.acquire(permits, timeout = INFINITE)

        // then: the permits should be granted
        assertEquals(permits, semaphoreState.permitsInUse)

        // when: waiting for the replenishment period to elapse
        delayWithRealTime(1.seconds)

        // then: the permits should be not have been replenished yet,
        //  because only on acquisition the replenishment happens
        assertEquals(permits, semaphoreState.permitsInUse)

        // when: acquiring permits within the limit
        val permitsAfterReplenishment = 50
        rateLimiter.acquire(permitsAfterReplenishment, timeout = INFINITE)

        // then: the permits should not only be granted but also replenished
        assertEquals(permitsAfterReplenishment, semaphoreState.permitsInUse)

    }

    @Test
    fun ifQueueFullAndTimeoutElapsesThenRequestShouldBeRejected() = runTest {
        // given: a rate limiter with 1-permit and queue length of 1
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(1, INFINITE, 1))
                baseTimeoutDuration = INFINITE
                onRejected { throw it }
            },
            semaphoreState
        )

        // when: acquiring permits within the limit
        rateLimiter.acquire(1, timeout = INFINITE)

        // then: the permits should be granted
        assertEquals(1, semaphoreState.permitsInUse)

        // when: acquiring 1 more permit outside the limit
        val duration = 1.seconds
        val measuredDuration = measureWithRealTime {
            assertFailsWith<RateLimiterRejectedException> {
                // then: it should be rejected as the queue is full
                withContext(Dispatchers.Default) { // to disable virtual time skipping
                    rateLimiter.acquire(1, timeout = duration)
                }
            }
        }

        // and: the permits should not have been granted
        assertEquals(1, semaphoreState.permitsInUse)

        // and: the request should be rejected after the timeout
        assertTrue(measuredDuration in duration..(duration + 1.seconds))
    }

    @Test
    fun pendingRequestInQueueShouldBeHandledInFifoOrder() = runTest {
        // given: a rate limiter with 1-permit and queue length of 2
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(1, INFINITE, 2))
                baseTimeoutDuration = INFINITE
                onRejected { throw it }
            },
            semaphoreState
        )

        // when: acquiring permits within the limit
        rateLimiter.acquire(1, timeout = INFINITE)

        // then: the permits should be granted
        assertEquals(1, semaphoreState.permitsInUse)

        // when: acquiring 2 more permits outside the limit
        val duration = 1.seconds
        val request1 = launch {
            withContext(Dispatchers.Default) { // to disable virtual time skipping
                rateLimiter.acquire(1, timeout = INFINITE)
            }
        }
        delayWithRealTime(250.milliseconds) // to ensure, the first request is enqueued
        val request2 = launch {
            withContext(Dispatchers.Default) { // to disable virtual time skipping
                rateLimiter.acquire(1, timeout = INFINITE)
            }
        }

        // and: the permits should not have been granted
        delayWithRealTime(duration)
        assertEquals(1, semaphoreState.permitsInUse)

        // when: releasing the permit
        rateLimiter.release(1)

        // then: the permits should be updated
        assertEquals(1, semaphoreState.permitsInUse)

        // and: the request should be granted
        delayWithRealTime(2.seconds)
        assertFalse(request1.isActive)
        assertTrue(request2.isActive)

        // when: releasing the permit
        rateLimiter.release(1)

        // then: the permits should be updated
        assertEquals(1, semaphoreState.permitsInUse)

        // and: the request should be granted
        delayWithRealTime(1.milliseconds)
        assertFalse(request2.isActive)
    }

    @Test
    fun shouldAcquireAndReleaseCorrectlyUsingCallFunction() = runTest {
        // given: a rate limiter with 1-permit and queue length of 1
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(1, INFINITE, 1))
                baseTimeoutDuration = INFINITE
                onRejected { throw it }
            },
            semaphoreState
        )

        // when: acquiring permits within the limit
        val permits = 1
        rateLimiter.call(permits, INFINITE) {
            // then: the permits should be granted
            assertEquals(permits, semaphoreState.permitsInUse)
        }

        // then: the permits should be released
        assertEquals(0, semaphoreState.permitsInUse)
    }

    @Test
    fun shouldUseBaseTimeConfigurationIfNotSpecifiedInCallFunction() = runTest {
        // given: a rate limiter with 1-permit, queue length of 1 and a base timeout duration
        val baseTimeoutDuration = 1.seconds randomTo 3.seconds
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(1, INFINITE, 1))
                this.baseTimeoutDuration = baseTimeoutDuration
                onRejected { throw it }
            },
            semaphoreState
        )

        // when: acquiring permits within the limit
        val request = launch {
            rateLimiter.call {
                // assume expensive operation
                delayWithRealTime(INFINITE)
            }
        }

        // then: the permits should be granted
        delayWithRealTime(250.milliseconds)
        assertEquals(1, semaphoreState.permitsInUse)

        // when: another request is made outside the limit
        val duration = measureWithRealTime {
            assertFailsWith<RateLimiterRejectedException> {
                // then: the request should be enqueued and rejected after the base timeout
                rateLimiter.call {
                    // assume expensive operation
                    delayWithRealTime(INFINITE)
                }
            }
        }

        // then: the permits should not have been granted
        assertEquals(1, semaphoreState.permitsInUse)

        // and: the request should be rejected after the base timeout
        assertTrue(duration in baseTimeoutDuration..(baseTimeoutDuration + 250.milliseconds))
        request.cancelAndJoin()
    }

    @Test
    fun rejectedRequestShouldHaveInformationForWhenToRetry() = runTest {
        // given: a rate limiter with 1-permit and queue length of 0
        val replenishmentPeriod = 2.seconds randomTo 5.seconds
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(1, replenishmentPeriod, 0))
                baseTimeoutDuration = INFINITE
                onRejected { throw it }
            },
            semaphoreState
        )

        // when: acquiring permits within the limit
        rateLimiter.acquire(1, timeout = INFINITE)

        // then: the permits should be granted
        assertEquals(1, semaphoreState.permitsInUse)

        // when: acquiring 1 more permit outside the limit
        lateinit var exception: RateLimiterRejectedException
        val duration = measureWithRealTime {
            exception = assertFailsWith<RateLimiterRejectedException> {
                // then: it should be rejected immediately as the queue length is 0
                rateLimiter.acquire(1, timeout = INFINITE)
            }
        }

        // then: the permits should not have been granted
        assertEquals(1, semaphoreState.permitsInUse)

        // and: the retry information should be below the replenishment period
        assertTrue(exception.retryAfter in (replenishmentPeriod - duration - 250.milliseconds)..replenishmentPeriod)
    }

    @Test
    fun onRejectedShouldBeCalledWhenRequestIsRejected() = runTest {
        // given: a rate limiter with 1-permit and queue length of 0
        var onRejectedCalled = false
        val rateLimiter = RateLimiter(
            rateLimiterConfig {
                algorithm(FixedWindowCounter(1, INFINITE, 0))
                baseTimeoutDuration = INFINITE
                onRejected {
                    onRejectedCalled = true
                    throw it
                }
            },
            semaphoreState
        )

        // when: acquiring permits within the limit
        rateLimiter.acquire(1, timeout = INFINITE)

        // then: the permits should be granted
        assertEquals(1, semaphoreState.permitsInUse)

        // when: acquiring 1 more permit outside the limit
        assertFailsWith<RateLimiterRejectedException> {
            // then: it should be rejected immediately as the queue length is 0
            rateLimiter.acquire(1, timeout = INFINITE)
        }

        // then: the permits should not have been granted
        assertEquals(1, semaphoreState.permitsInUse)

        // and: the onRejected should have been called
        assertTrue(onRejectedCalled)
    }

    @Test
    fun shouldThrowExceptionIfNrOfPermitsIsNotPositive() = runTest {
        // given: a rate limiter
        val rateLimiter = RateLimiter()

        // when: acquiring an invalid number of permits
        assertFailsWith<IllegalArgumentException> {
            // then: it should throw an exception
            rateLimiter.acquire(0, timeout = INFINITE)
        }

        // when: releasing an invalid number of permits
        assertFailsWith<IllegalArgumentException> {
            // then: it should throw an exception
            rateLimiter.release(0)
        }
    }

    @Test
    fun shouldThrowExceptionIfTryingToReleaseMorePermitsThanAcquired() = runTest {
        // given: a rate limiter
        val rateLimiter = RateLimiter()

        // when: releasing more permits than acquired
        assertFailsWith<IllegalArgumentException> {
            // then: it should throw an exception
            rateLimiter.release(1)
        }
    }

    @Test
    fun shouldThrowExceptionIfTimeoutIsNegative() = runTest {
        // given: a rate limiter
        val rateLimiter = RateLimiter()

        // when: acquiring permits with a negative timeout
        assertFailsWith<IllegalArgumentException> {
            // then: it should throw an exception
            rateLimiter.acquire(1, timeout = (-1).seconds)
        }
    }

    // TODO: stress test with multiple requests and multiple rate limiters
}
