package kresil.ratelimiter.semaphore.implementations

import kresil.core.semaphore.SuspendableSemaphore
import kresil.core.timemark.getCurrentTimeMark
import kresil.core.timemark.getRemainingDuration
import kresil.core.timemark.hasExceededDuration
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kresil.ratelimiter.semaphore.RateLimiterSemaphore
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.coroutines.cancellation.CancellationException

/**
 * A counting suspendable [Semaphore](https://docs.oracle.com/javase%2F8%2Fdocs%2Fapi%2F%2F/java/util/concurrent/Semaphore.html)
 * implementation of a rate limiter that uses the fixed window counter-algorithm to control the number of permits available for requests.
 * Uses fixed time windows (e.g., 1 second, 1 minute) to enforce the rate limit.
 * At the beginning of each time window, the counter is reset, and requests are counted within that window.
 * This implementation behaviour is dependent on the configuration provided (e.g., total permits, queue length, etc.).
 *
 * ##### Considerations
 * It's important to note that the fixed window counter algorithm can lead to bursty traffic patterns,
 * as requests are not evenly distributed within the time window. For example, if the rate limit is 5 requests per minute
 * and a user makes 5 requests at the end of the time window,
 * they can circumvent the limit by making 5 more requests at the beginning of the next time window
 * (doubling the allowed requests).
 * It can also lead to a stampeding effect,
 * where previously rejected requests are retried simultaneously when the time window resets, causing spikes in traffic and overload the system, especially when dealing with a large number of clients.
 * @param config The configuration for the rate limiter mechanism.
 * @param semaphoreState The state of the semaphore which includes an internal in-memory queue to store the excess requests.
 * @throws CancellationException if the coroutine is cancelled while waiting for the permits to be available.
 * @throws RateLimiterRejectedException if the request is rejected due to the queue being full or the acquisition timeout being exceeded.
 * @see SuspendableSemaphore
 */
internal class FixedWindowCounterSemaphore(
    private val config: RateLimiterConfig,
    private val semaphoreState: SemaphoreState,
) : RateLimiterSemaphore(config, semaphoreState) {

    override fun createRateLimitedException(permits: Int): RateLimiterRejectedException {
        return RateLimiterRejectedException(
            // calculate the time left until the next window boundary
            // doesn't take into account the acquisition permits because this algorithm resets the permits
            // at the end of the window
            retryAfter = getRemainingDuration(currentRefreshTimeMark, config.algorithm.refreshPeriod),
        )
    }

    override suspend fun refreshSemaphoreState() {
        if (hasExceededDuration(currentRefreshTimeMark, config.algorithm.refreshPeriod)) {
            semaphoreState.setPermits { _ -> 0 }
            semaphoreState.setRefreshTimeMark(getCurrentTimeMark())
        }
    }

}
