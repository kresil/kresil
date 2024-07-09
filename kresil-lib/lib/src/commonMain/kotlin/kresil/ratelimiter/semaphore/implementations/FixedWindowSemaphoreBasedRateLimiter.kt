package kresil.ratelimiter.semaphore.implementations

import kresil.core.semaphore.SuspendableSemaphore
import kresil.core.timemark.getRemainingDuration
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kresil.ratelimiter.semaphore.SemaphoreBasedRateLimiter
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * A counting suspendable [Semaphore](https://docs.oracle.com/javase%2F8%2Fdocs%2Fapi%2F%2F/java/util/concurrent/Semaphore.html)
 * based implementation of a rate limiter that uses the fixed window counter-algorithm to control the number of permits available for requests.
 * Uses fixed time windows (e.g., 1 second, 1 minute) to enforce the rate limit.
 * At the beginning of each time window, the counter is reset, and requests are counted within that window.
 * This implementation behaviour is dependent on the configuration provided (e.g., total permits, queue length, etc.).
 *
 * ##### Considerations
 * It's important to note that the fixed window counter algorithm can lead to:
 * - Bursts of requests around the boundary time of the fixed window, may result in strained resources as
 * the window counter is reset in the middle of the traffic burst.
 * - A stampeding effect, where previously rejected requests are retried simultaneously when the time window
 * resets, causing spikes in traffic and overload the system, especially when dealing with a large number
 * of clients.
 * @param config The configuration for the rate limiter mechanism.
 * @param semaphoreState The state of the semaphore which includes an internal in-memory queue to store the excess requests.
 * @throws CancellationException if the coroutine is cancelled while waiting for the permits to be available.
 * @throws RateLimiterRejectedException if the request is rejected due to the queue being full or the acquisition timeout being exceeded.
 * @see SuspendableSemaphore
 */
internal class FixedWindowSemaphoreBasedRateLimiter(
    private val config: RateLimiterConfig,
    private val semaphoreState: SemaphoreState,
) : SemaphoreBasedRateLimiter(config, semaphoreState) {

    // calculate the time left until the next window boundary
    override fun calculateRetryDuration(permits: Int): Duration =
        getRemainingDuration(currentReplenishmentTimeMark, config.algorithm.replenishmentPeriod)

    override suspend fun replenishSemaphoreState() {
        semaphoreState.setPermits { _ -> 0 }
    }

}
