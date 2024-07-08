package kresil.ratelimiter.semaphore.state

import kresil.core.utils.CircularDoublyLinkedList
import kotlin.time.ComparableTimeMark
import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.semaphore.request.RateLimitedRequest

/**
 * Represents the abstract state of a semaphore used by a [RateLimiter] which,
 * depending on the implementation, can be stored in-memory or externally (e.g., in a database or cache).
 * Additionally, an internal queue is used to store the requests that are waiting for permits to be available.
 * If this state is to be shared between multiple instances of the rate limiter, it should be thread-safe.
 * If not, it is protected by the rate limiter's internal synchronization mechanisms.
 *
 * The state enforces a disposable pattern by implementing the [AutoCloseable] interface.
 * @see RateLimiter
 */
@OptIn(ExperimentalStdlibApi::class) // TODO: is stable in 2.0.0
abstract class SemaphoreState : AutoCloseable {

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
     * The time mark indicating when the semaphore state was last replenished.
     * It is used to determine when the rate limiting replenishment period has passed.
     * @see setReplenishmentTimeMark
     */
    abstract val replenishmentTimeMark: ComparableTimeMark

    /**
     * Updates the number of permits optionally considering the current value.
     * @param updateFunction A function that updates the number of permits considering the current value.
     * @see permitsInUse
     */
    abstract fun setPermits(updateFunction: (Int) -> Int)
    // TODO: might have to be suspend

    /**
     * Sets a new replenishment time mark by essentially taking a snapshot of the current time.
     * @see replenishmentTimeMark
     */
    abstract fun setReplenishmentTimeMark(value: ComparableTimeMark)
}
