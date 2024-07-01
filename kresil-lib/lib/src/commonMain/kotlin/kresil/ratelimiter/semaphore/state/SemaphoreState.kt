package kresil.ratelimiter.semaphore.state

import kotlin.time.ComparableTimeMark
import kresil.ratelimiter.RateLimiter

/**
 * Represents the abstract state of a semaphore used by a [RateLimiter] which,
 * depending on the implementation, can be stored in-memory or externally (e.g., in a database).
 * The state does not need to be thread-safe as it is managed by the rate limiter.
 */
interface SemaphoreState {

    /**
     * The number of permits currently in use (i.e., permits that have been acquired but not yet released).
     * @see setPermits
     */
    val permitsInUse: Int

    /**
     * The time mark indicating when the semaphore state was last refreshed.
     * It is used to determine when the rate limiting period has expired and the permits need to be reset.
     * @see setRefreshTimeMark
     */
    val refreshTimeMark: ComparableTimeMark

    /**
     * Updates the number of permits using the provided update function.
     * @param updateFunction A function that updates the number of permits considering the current value.
     * @see permitsInUse
     */
    fun setPermits(updateFunction: (Int) -> Int)

    /**
     * Sets a new refresh time mark by essentially taking a snapshot of the current time.
     * @see refreshTimeMark
     */
    fun setRefreshTimeMark(value: ComparableTimeMark)
}
