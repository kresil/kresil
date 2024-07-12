package kresil.ratelimiter.exceptions

import kotlin.time.Duration

/**
 * Exception that is thrown when a request is rejected by the rate limiter due to the queue being full
 * or the acquisition timeout being exceeded.
 * @property retryAfter The minimum duration to wait before retrying the request.
 */
class RateLimiterRejectedException(val retryAfter: Duration) : IllegalStateException(
    "Rate limiter has rejected the request. Retry after $retryAfter."
)

