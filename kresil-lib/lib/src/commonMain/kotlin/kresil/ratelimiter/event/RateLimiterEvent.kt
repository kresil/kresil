package kresil.ratelimiter.event

import kresil.ratelimiter.RateLimiter
import kotlin.time.Duration

/**
 * Represents all possible [RateLimiter] events that can be triggered.
 */
sealed class RateLimiterEvent { // TODO: Implement events

    /**
     * Represents an event triggered when a permit is successfully acquired, and the request is allowed to proceed.
     * @param permits The number of permits acquired.
     */
    data class Success(val permits: Int) : RateLimiterEvent()

    /**
     * Represents an event triggered when a permit acquisition is rejected due to queue full.
     * @param permits The number of permits that were attempted to be acquired.
     */
    data class Reject(val permits: Int, val retryAfter: Duration) : RateLimiterEvent()

    /**
     * Represents an event triggered when a request is queued due to insufficient permits available.
     * @param permits The number of permits that were attempted to be acquired.
     * @param timeout The duration to wait for permits to be available.
     */
    data class Queued(val permits: Int, val timeout: Duration) : RateLimiterEvent()
}
