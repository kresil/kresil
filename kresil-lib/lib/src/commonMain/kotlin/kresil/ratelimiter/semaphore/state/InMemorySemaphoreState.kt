package kresil.ratelimiter.semaphore.state

import kresil.core.timemark.getCurrentTimeMark
import kotlin.time.ComparableTimeMark

/**
 * Represents an in-memory state of a semaphore.
 */
internal class InMemorySemaphoreState : SemaphoreState {
    override var permitsInUse: Int = 0
        private set

    override var refreshTimeMark: ComparableTimeMark = getCurrentTimeMark()
        private set

    override fun setPermits(updateFunction: (Int) -> Int) {
        permitsInUse = updateFunction(permitsInUse)
    }

    override fun setRefreshTimeMark(value: ComparableTimeMark) {
        refreshTimeMark = value
    }
}
