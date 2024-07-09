package kresil.ratelimiter.config

import kresil.core.callbacks.ExceptionHandler
import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm
import kotlin.time.Duration

/**
 * Represents a [RateLimiter] configuration.
 * @param algorithm The algorithm that will be used to determine the rate limiting behavior.
 * See [RateLimitingAlgorithm] for more details.
 * @param baseTimeoutDuration The default duration a request will be placed in the queue if rate-limited.
 * After this duration, the request will be rejected.
 * @param onRejected The exception handler that will be called when a request is rejected by the rate limiter.
 * @see RateLimiter
 */
data class RateLimiterConfig(
    val algorithm: RateLimitingAlgorithm,
    val baseTimeoutDuration: Duration,
    val onRejected: ExceptionHandler
)
