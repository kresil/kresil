package kresil.ratelimiter.semaphore.state

import kresil.core.timemark.getCurrentTimeMark
import kotlin.time.ComparableTimeMark

/**
 * Represents an in-memory state of a semaphore.
 */
internal class InMemorySemaphoreState : SemaphoreState() {
    override var permitsInUse: Int = 0
        private set

    override var replenishmentTimeMark: ComparableTimeMark = getCurrentTimeMark()
        private set

    override fun setPermits(updateFunction: (Int) -> Int) {
        permitsInUse = updateFunction(permitsInUse)
    }

    override fun setReplenishmentTimeMark(value: ComparableTimeMark) {
        replenishmentTimeMark = value
    }

    override fun close() {
        // no-operation, GC will take care of the rest
    }
}
