package kresil.ratelimiter.config

import kresil.core.callbacks.ExceptionHandler
import kresil.ratelimiter.RateLimiter
import kotlin.time.Duration

/**
 * Represents a [RateLimiter] configuration.
 * @param permits The number of permits that the rate limiter will allow with an unique key in a given [refreshPeriod].
 * @param refreshPeriod The time period in which the rate limiter will allow [permits] requests per key to be made.
 * @param queueLength The maximum number of requests that can be queued per key when the rate limiter is exceeded.
 * @param baseTimeoutDuration The default duration a request will be placed in the queue if the rate limiter is full. After this duration, the request will be rejected.
 * @param onRejected The exception handler that will be called when a request is rejected by the rate limiter.
 */
data class RateLimiterConfig(
    val permits: Int,
    val refreshPeriod: Duration,
    val queueLength: Int,
    val baseTimeoutDuration: Duration,
    val onRejected: ExceptionHandler,
)
