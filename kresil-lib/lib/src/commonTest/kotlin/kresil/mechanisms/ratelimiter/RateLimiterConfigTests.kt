package kresil.mechanisms.ratelimiter

import kotlinx.coroutines.test.runTest
import kresil.core.callbacks.ExceptionHandler
import kresil.exceptions.WebServiceException
import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.FixedWindowCounter
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RateLimiterConfigTests {

    @Test
    fun defaultConfigShouldBeUsedWhenNoConfigIsProvided() = runTest {
        // given: a rate limiter instance with no configuration
        val rateLimiter = RateLimiter()
        val config = rateLimiter.config
        val algorithm = config.algorithm

        // then: the rate limiter should use the default configuration
        assertEquals(1000, algorithm.totalPermits)
        assertEquals(1.minutes, algorithm.replenishmentPeriod)
        assertEquals(0, algorithm.queueLength)
        assertEquals(10.seconds, config.baseTimeoutDuration)
        assertFailsWith<RateLimiterRejectedException> {
            config.onRejected(RateLimiterRejectedException(retryAfter = ZERO))
        }
    }

    @Test
    fun totalPermitsShouldBeGreaterThanZero() = runTest {
        // given: a rate limiter configuration with total permits set to 0
        val ex = assertFailsWith<IllegalArgumentException> {
            rateLimiterConfig {
                algorithm(FixedWindowCounter(0, 1.minutes, 50))
            }
        }

        // then: an exception should be thrown
        assertEquals("Total permits must be greater than or equal to 1", ex.message)
    }

    @Test
    fun queueLengthShouldBeNonNegative() = runTest {
        // given: a rate limiter configuration with queue length set to -1
        val ex = assertFailsWith<IllegalArgumentException> {
            rateLimiterConfig {
                algorithm(FixedWindowCounter(100, 1.minutes, -1))
            }
        }

        // then: an exception should be thrown
        assertEquals("Queue length must be greater than or equal to 0", ex.message)
    }

    @Test
    fun baseTimeoutDurationShouldBeNonNegative() = runTest {
        // given: a rate limiter configuration with base timeout duration set to -5 seconds
        val ex = assertFailsWith<IllegalArgumentException> {
            rateLimiterConfig {
                baseTimeoutDuration = (-5).seconds
            }
        }

        // then: an exception should be thrown
        assertEquals("Base timeout duration must be greater than or equal to zero", ex.message)
    }

    @Test
    fun customOnRejectedHandlerShouldBeSet() = runTest {
        // given: a custom exception handler
        val customHandler: ExceptionHandler = { throw WebServiceException("BAM!") }
        val rateLimiterConfig = rateLimiterConfig {
            // when: the custom onRejected handler is set
            onRejected(customHandler)
        }

        // then: the rate limiter should use the custom onRejected handler
        assertFailsWith<WebServiceException> {
            rateLimiterConfig.onRejected(RateLimiterRejectedException(retryAfter = ZERO))
        }
    }

    @Test
    fun refresPeriodMustBePositiveDuration() = runTest {
        // given: a rate limiter configuration with a replenishment period set to 0
        val ex = assertFailsWith<IllegalArgumentException> {
            rateLimiterConfig {
                algorithm(FixedWindowCounter(100, ZERO, 50))
            }
        }

        // then: an exception should be thrown
        assertEquals("Replenishment period duration must be greater than zero", ex.message)
    }
}
