package kresil.ratelimiter.semaphore.state

import kresil.core.utils.CircularDoublyLinkedList
import kotlin.time.ComparableTimeMark
import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.semaphore.request.RateLimitedRequest

/**
 * Represents the abstract state of a semaphore used by a [RateLimiter] which,
 * depending on the implementation, can be stored in-memory or externally (e.g., in a database or cache).
 * The state does not need to be thread-safe as it is managed by the rate limiter.
 * Additionally, an internal queue is used to store the requests that are waiting for permits to be available.
 */
abstract class SemaphoreState {

    /**
     * The queue to store the requests that are waiting for permits to be available.
     */
    internal val queue = CircularDoublyLinkedList<RateLimitedRequest>()

    /**
     * The number of permits currently in use (i.e., permits that have been acquired but not yet released).
     * @see setPermits
     */
    abstract val permitsInUse: Int

    /**
     * The time mark indicating when the semaphore state was last refreshed.
     * It is used to determine when the rate limiting period has expired and the permits need to be reset.
     * @see setRefreshTimeMark
     */
    abstract val refreshTimeMark: ComparableTimeMark

    /**
     * Updates the number of permits using the provided update function.
     * @param updateFunction A function that updates the number of permits considering the current value.
     * @see permitsInUse
     */
    abstract fun setPermits(updateFunction: (Int) -> Int)

    /**
     * Sets a new refresh time mark by essentially taking a snapshot of the current time.
     * @see refreshTimeMark
     */
    abstract fun setRefreshTimeMark(value: ComparableTimeMark)
}
