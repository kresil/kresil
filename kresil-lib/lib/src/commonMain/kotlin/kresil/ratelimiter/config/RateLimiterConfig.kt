package kresil.ratelimiter.config

import kresil.core.callbacks.ExceptionHandler
import kresil.ratelimiter.RateLimiter
import kotlin.time.Duration

/**
 * Represents a [RateLimiter] configuration.
 * @param totalPermits The total number of permits that can be allowed in a given [refreshPeriod].
 * @param refreshPeriod The time period in which the rate limiter will allow [totalPermits] permits.
 * @param queueLength The maximum number of requests that can be queued per key when the rate limiter is exceeded.
 * @param baseTimeoutDuration The default duration a request will be placed in the queue if the rate limiter is full. After this duration, the request will be rejected.
 * @param onRejected The exception handler that will be called when a request is rejected by the rate limiter.
 */
data class RateLimiterConfig(
    val totalPermits: Int,
    val refreshPeriod: Duration,
    val queueLength: Int,
    val baseTimeoutDuration: Duration,
    val onRejected: ExceptionHandler,
)
