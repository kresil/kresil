package kresil.ratelimiter.event

import kresil.ratelimiter.RateLimiter

/**
 * Represents all possible [RateLimiter] events that can be triggered.
 */
sealed class RateLimiterEvent {

    /**
     * Represents an event triggered when a permit is successfully acquired, and the request is allowed to proceed.
     * @param key The key for which the permit was acquired.
     * @param permits The number of permits acquired.
     */
    data class Success(val key: String, val permits: Int) : RateLimiterEvent()

    /**
     * Represents an event triggered when a permit acquisition is rejected due to queue full for requests grouped by the same key.
     * @param key The key for which the permit acquisition was rejected.
     * @param permits The number of permits that were attempted to be acquired.
     */
    data class Reject(val key: String, val permits: Int) : RateLimiterEvent()

    /**
     * Represents an event triggered when a request is queued due to insufficient permits available overall
     * or for the specific key.
     * @param key The key for which the request is queued.
     * @param permits The number of permits that were attempted to be acquired.
     */
    data class Queued(val key: String, val permits: Int) : RateLimiterEvent()
}
