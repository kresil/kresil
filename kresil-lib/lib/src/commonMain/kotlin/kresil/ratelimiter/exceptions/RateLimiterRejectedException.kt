package kresil.ratelimiter.exceptions

/**
 * Exception that is thrown when a request is rejected by the rate limiter due the queue being full.
 * @param message The message to be displayed when the exception is thrown.
 */
internal class RateLimiterRejectedException(message: String) : IllegalStateException(message)
