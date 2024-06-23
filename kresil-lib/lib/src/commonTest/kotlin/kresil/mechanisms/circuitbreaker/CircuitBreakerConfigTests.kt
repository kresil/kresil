package kresil.mechanisms.circuitbreaker

import kotlinx.coroutines.test.runTest
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.config.circuitBreakerConfig
import kresil.circuitbreaker.slidingwindow.SlidingWindowType
import kresil.circuitbreaker.state.CircuitBreakerState
import kresil.core.delay.strategy.DelayStrategyOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CircuitBreakerConfigTests {

    @Test
    fun defaultConfigShouldBeUsedWhenNoConfigIsProvided() = runTest {
        // given: a circuit breaker instance with no configuration
        val circuitBreaker = CircuitBreaker()

        // then: the circuit breaker should be in the Closed state
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: the circuit breaker should use the default configuration
        val config = circuitBreaker.config
        assertEquals(0.5, config.failureRateThreshold)
        val (size, minimumThroughput, type) = config.slidingWindow
        assertEquals(100, size)
        assertEquals(100, minimumThroughput)
        assertEquals(SlidingWindowType.COUNT_BASED, type)
        assertEquals(10, config.permittedNumberOfCallsInHalfOpenState)
        val constantDuration = DelayStrategyOptions.constant(1.minutes).invoke(Int.MAX_VALUE)
        for (i in 1..100) {
            assertEquals(constantDuration, config.delayStrategyInOpenState(i, Unit))
        }
        assertEquals(Duration.ZERO, config.maxWaitDurationInHalfOpenState)
        assertFalse(config.recordResultPredicate(Any()))
        assertTrue(config.recordExceptionPredicate(Exception()))
        assertTrue(config.recordExceptionPredicate(Error()))
    }

    @Test
    fun failureRateThreshouldShouldBeGreaterThanZero() = runTest {
        // given: a circuit breaker configuration with a failure rate threshold of 0
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the failure rate threshold is set to 0.0
                failureRateThreshold = 0.0
            }
        }

        // then: an exception should be thrown
        assertEquals("Failure rate threshold must be between 0 exclusive and 1 inclusive", ex.message)
    }

    @Test
    fun failureRateThreshouldShouldBeBelowOrEqualToOne() = runTest {
        // given: a circuit breaker configuration with a failure rate threshold of 1.1
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the failure rate threshold is set to 1.1
                failureRateThreshold = 1.1
            }
        }

        // then: an exception should be thrown
        assertEquals("Failure rate threshold must be between 0 exclusive and 1 inclusive", ex.message)

    }

    @Test
    fun slidingWindowSizeShouldBeGreaterThanZero() = runTest {
        // given: a circuit breaker configuration with a sliding window size of 0
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the sliding window size is set to 0
                slidingWindow(0, 100)
            }
        }

        // then: an exception should be thrown
        assertEquals("Sliding window size must be greater than 0", ex.message)
    }

    @Test
    fun minimumThroughputShouldBeGreaterThanZero() = runTest {
        // given: a circuit breaker configuration with a minimum throughput of 0
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the minimum throughput is set to 0
                slidingWindow(100, 0)
            }
        }

        // then: an exception should be thrown
        assertEquals("Minimum throughput must be greater than 0", ex.message)
    }

    @Test
    fun permittedNumberOfCallsInHalfOpenStateShouldBeGreaterThanOrEqualToZero() = runTest {
        // given: a circuit breaker configuration with a permitted number of calls in HalfOpen state of -1
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the permitted number of calls in HalfOpen state is set to -1
                permittedNumberOfCallsInHalfOpenState = -1
            }
        }

        // then: an exception should be thrown
        assertEquals("Permitted number of calls in HalfOpen state must be greater than 0", ex.message)
    }

    @Test
    fun waitDurationInHalfOpenStateShouldBeNonNegative() = runTest {
        // given: a circuit breaker configuration with a wait duration in HalfOpen state of -1 second
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the wait duration in HalfOpen state is set to -1 second
                maxWaitDurationInHalfOpenState = (-1).seconds
            }
        }

        // then: an exception should be thrown
        assertEquals("HalfOpen state duration must be greater than or equal to zero", ex.message)
    }

}
