package kresil.ratelimiter.exceptions

/**
 * Exception that is thrown when a request is rejected by the rate limiter due the queue being full.
 */
internal class RateLimiterRejectedException : IllegalStateException("Rate limiter has rejected the request")
