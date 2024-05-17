package circuitbreaker

import kotlinx.coroutines.test.runTest
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.config.circuitBreakerConfig
import kresil.circuitbreaker.state.CircuitBreakerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CircuitBreakerConfigTests {

    @Test
    fun defaultConfigShouldBeUsedWhenNoConfigIsProvided() = runTest {
        // given: a circuit breaker instance with no configuration
        val circuitBreaker = CircuitBreaker()

        // then: the circuit breaker should be in the CLOSED state
        assertSame(CircuitBreakerState.CLOSED, circuitBreaker.currentState())

        // and: the circuit breaker should use the default configuration
        val config = circuitBreaker.config
        assertEquals(0.5, config.failureRateThreshold)
        assertEquals(100, config.slidingWindowSize)
        assertEquals(100, config.minimumThroughput)
        assertEquals(10, config.permittedNumberOfCallsInHalfOpenState)
        assertEquals(60.seconds, config.waitDurationInOpenState)
        assertEquals(25.seconds, config.waitDurationInHalfOpenState)
        assertFalse(config.recordSuccessAsFailurePredicate(Any()))
        assertTrue(config.recordExceptionPredicate(Exception()))
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
        assertEquals("Failure rate threshold must be between 0.0 exclusive and 1.0 inclusive", ex.message)
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
        assertEquals("Failure rate threshold must be between 0.0 exclusive and 1.0 inclusive", ex.message)

    }

    @Test
    fun slidingWindowSizeShouldBeGreaterThanZero() = runTest {
        // given: a circuit breaker configuration with a sliding window size of 0
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the sliding window size is set to 0
                slidingWindowSize = 0
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
                minimumThroughput = 0
            }
        }

        // then: an exception should be thrown
        assertEquals("Minimum throughput must be greater than 0", ex.message)
    }

    @Test
    fun permittedNumberOfCallsInHalfOpenStateShouldBeGreaterThanOrEqualToZero() = runTest {
        // given: a circuit breaker configuration with a permitted number of calls in HALF_OPEN state of -1
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the permitted number of calls in HALF_OPEN state is set to -1
                permittedNumberOfCallsInHalfOpenState = -1
            }
        }

        // then: an exception should be thrown
        assertEquals("Permitted number of calls in HALF_OPEN state must be greater than or equal to 0", ex.message)
    }

    @Test
    fun waitDurationInOpenStateShouldBeNonNegative() = runTest {
        // given: a circuit breaker configuration with a wait duration in OPEN state of -1 second
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the wait duration in OPEN state is set to -1 second
                waitDurationInOpenState = (-1).seconds
            }
        }

        // then: an exception should be thrown
        assertEquals("OPEN state duration must be greater than or equal to 0", ex.message)
    }

    @Test
    fun waitDurationInHalfOpenStateShouldBeNonNegative() = runTest {
        // given: a circuit breaker configuration with a wait duration in HALF_OPEN state of -1 second
        val ex = assertFailsWith<IllegalArgumentException> {
            circuitBreakerConfig {
                // when: the wait duration in HALF_OPEN state is set to -1 second
                waitDurationInHalfOpenState = (-1).seconds
            }
        }

        // then: an exception should be thrown
        assertEquals("HALF_OPEN state duration must be greater than or equal to 0", ex.message)
    }

}
