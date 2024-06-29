package kresil.mechanisms.ratelimiter

import kotlinx.coroutines.test.runTest
import kresil.core.callbacks.ExceptionHandler
import kresil.exceptions.WebServiceException
import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RateLimiterConfigTests {

    @Test
    fun defaultConfigShouldBeUsedWhenNoConfigIsProvided() = runTest {
        // given: a rate limiter instance with no configuration
        val rateLimiter = RateLimiter()
        val config = rateLimiter.config

        // then: the rate limiter should use the default configuration
        assertEquals(100, config.totalPermits)
        assertEquals(1.minutes, config.refreshPeriod)
        assertEquals(50, config.queueLength)
        assertEquals(10.seconds, config.baseTimeoutDuration)
        assertFailsWith<RateLimiterRejectedException> {
            config.onRejected(RateLimiterRejectedException())
        }
    }

    @Test
    fun totalPermitsShouldBeGreaterThanOrEqualToOne() = runTest {
        // given: a rate limiter configuration with total permits set to 0
        val ex = assertFailsWith<IllegalArgumentException> {
            rateLimiterConfig {
                totalPermits = 0
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
                queueLength = -1
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
        // given: a custom onRejected handler
        val customHandler: ExceptionHandler = { throw WebServiceException("BAM!") }
        val rateLimiterConfig = rateLimiterConfig {
            // when: the custom onRejected handler is set
            onRejected(customHandler)
        }

        // then: the rate limiter should use the custom onRejected handler
        assertFailsWith<WebServiceException> {
            rateLimiterConfig.onRejected(RateLimiterRejectedException())
        }
    }
}
