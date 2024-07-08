package kresil.ratelimiter.algorithm

import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.FixedWindowCounter
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.SlidingWindowCounter
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.TokenBucket
import kotlin.time.Duration

/**
 * Represents all possible rate limiting algorithms that can be used to determine the behaviour of a [RateLimiter].
 * @property totalPermits The total number of permits that can be allowed in a given [replenishmentPeriod].
 * @property replenishmentPeriod The amount of time that must pass before the rate limiting algorithm replenishes
 * permits.
 * Depends on the algorithm.
 * @property queueLength The maximum number of requests that can be queued when the rate limiter is exceeded. If
 * set to 0, the rate limiter will reject requests immediately when the limit is reached.
 * @see FixedWindowCounter
 * @see TokenBucket
 * @see SlidingWindowCounter
 */
sealed class RateLimitingAlgorithm(
    open val totalPermits: Int,
    open val replenishmentPeriod: Duration,
    open val queueLength: Int,
) {

    /**
     * Represents a rate limiting algorithm that will allow a fixed number of permits in a given time period
     * (e.g., 5 requests per minute).
     * At the beginning of each time window, the counter is reset, and requests are counted from that point.
     *
     * ##### Considerations
     * It's important to note that the fixed window counter algorithm can lead to:
     * - Bursts of requests around the boundary time of the fixed window, may result in strained resources as
     * the window counter is reset in the middle of the traffic burst.
     * - A stampeding effect, where previously rejected requests are retried simultaneously when the time window
     * resets, causing spikes in traffic and overload the system, especially when dealing with a large number
     * of clients.
     * @param replenishmentPeriod The time period that the rate limiter will use to reset the counter.
     * @see TokenBucket
     * @see SlidingWindowCounter
     */
    data class FixedWindowCounter(
        override val totalPermits: Int,
        override val replenishmentPeriod: Duration,
        override val queueLength: Int,
    ) : RateLimitingAlgorithm(totalPermits, replenishmentPeriod, queueLength)
    // TODO: check the possibility of implementing a fixed window counter with a sliding window counter with 1 segment

    /**
     * Represents a rate limiting algorithm that has a fixed bucket size and refills the bucket with tokens
     * at a constant rate.
     * At each [replenishmentPeriod], the bucket is refilled with **1 token** (e.g., plus 1 token per second),
     * never exceeding the [totalPermits] limit.
     *
     * ##### Considerations
     * The token bucket algorithm is more flexible than the fixed window counter algorithm because it allows
     * for bursts of requests up to the bucket size.
     *
     * @param replenishmentPeriod The time period that the rate limiter will use to refill the bucket with **1 more token**.
     * @see FixedWindowCounter
     * @see SlidingWindowCounter
     */
    data class TokenBucket(
        override val totalPermits: Int,
        override val replenishmentPeriod: Duration,
        override val queueLength: Int,
    ) : RateLimitingAlgorithm(totalPermits, replenishmentPeriod, queueLength)

    /**
     * Represents a rate limiting algorithm that divides the total time period into smaller segments or windows.
     * Each segment has its own counter, and the rate limiter keeps track of the number of requests in each
     * segment to provide a more accurate and smoother rate limiting mechanism.
     * After [segments] * [replenishmentPeriod], which emcompasses a window cycle, the oldest segment is removed, and
     * a new segment is added.
     * At any point, when a request arrives, the total number of requests in all segments is compared to the
     * [totalPermits] limit.
     *
     * ##### Considerations
     * This algorithm mitigates the burstiness at the boundary of fixed window implmentations by
     * distributing requests more evenly across time segments (e.g., if the current window is 25% through,
     * the previous window's count is weighted by 75%).
     * @param replenishmentPeriod The duration of each time segment.
     * @param segments The number of segments that the time window is divided into.
     * @see FixedWindowCounter
     * @see TokenBucket
     */
    data class SlidingWindowCounter(
        override val totalPermits: Int,
        override val replenishmentPeriod: Duration,
        val segments: Int,
        override val queueLength: Int,
    ) : RateLimitingAlgorithm(totalPermits, replenishmentPeriod, queueLength)
}
