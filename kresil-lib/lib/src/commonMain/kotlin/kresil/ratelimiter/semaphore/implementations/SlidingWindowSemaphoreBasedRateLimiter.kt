package kresil.ratelimiter.semaphore.implementations

import kresil.core.semaphore.SuspendableSemaphore
import kresil.core.timemark.getRemainingDuration
import kresil.core.utils.RingBuffer
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.SlidingWindowCounter
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kresil.ratelimiter.semaphore.SemaphoreBasedRateLimiter
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * A counting suspendable [Semaphore](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Semaphore.html)
 * based implementation of a rate limiter that uses the sliding window counter algorithm to control the number of permits available for requests.
 * The sliding window counter algorithm divides the time window into smaller segments and maintains a count of requests in each segment.
 * As time progresses, the window slides over these segments to provide a smoother rate limiting mechanism compared to fixed window counters.
 *
 * ##### Considerations
 * The sliding window counter algorithm helps to prevent bursts of requests and provides a more even distribution of traffic.
 * @param config The configuration for the rate limiter mechanism.
 * @param semaphoreState The state of the semaphore which includes an internal in-memory queue to store the excess requests.
 * @throws CancellationException if the coroutine is cancelled while waiting for the permits to be available.
 * @throws RateLimiterRejectedException if the request is rejected due to the queue being full or the acquisition timeout being exceeded.
 * @see SuspendableSemaphore
 */
internal class SlidingWindowSemaphoreBasedRateLimiter(
    private val config: RateLimiterConfig,
    private val semaphoreState: SemaphoreState,
) : SemaphoreBasedRateLimiter(config, semaphoreState) {

    private val algorithm: SlidingWindowCounter = config.algorithm as? SlidingWindowCounter ?: error {
        "Algorithm in configured is not a ${SlidingWindowCounter::class.simpleName}"
    }
    private val currentSegment
        get() = slidingWindow.pix + 1
    private val slidingWindow = RingBuffer<Int>(algorithm.segments).apply {
        add(algorithm.totalPermits) // first segment has all permits initially
    }

    override fun calculateRetryDuration(permits: Int): Duration {
        val permitsInUse = semaphoreState.permitsInUse
        val permitsLeft = algorithm.totalPermits - permitsInUse
        // are there any permits left on the current segment?
        return getRemainingDuration(
            currentReplenishmentTimeMark, if (permitsLeft >= permits) {
                // if so, get the remaining duration of the current segment
                algorithm.replenishmentPeriod
            } else {
                // if not, get the remaining duration up to the cycle end
                algorithm.replenishmentPeriod * currentSegment
            }
        )
    }

    override suspend fun replenishSemaphoreState() {
        /**
         * Example with 3 segments and 10 total permits:
         * ```
         *                                  +------ Has come full cycle
         *                                  |
         *                +-----+-----+-----+------ This function is called at the beginning of each segment
         *                |     |     |     |     |
         * Segments:      V (1) V (2) V (3) V (1) V
         *                |-----|-----|-----|-----|
         * Permits Used:  |  8  |  2  |  0  |  ?  |
         *                |-----|-----|-----|-----|
         *
         * Sliding Window: [8, ?, ?] -> [8, 2, ?] -> [8, 2, 0] -> [8, 2, 0]
         * ```
         */
        val hasComeFullCycle = slidingWindow.size == algorithm.segments
        val leftOverPermits = algorithm.totalPermits - semaphoreState.permitsInUse
        if (hasComeFullCycle) {
            // grab recorded permits in the oldest segment
            val permitsInOldestSegment = slidingWindow.eldestEntry
            // and remove it (note that this (add) operation will remove the oldest segment when adding a new one)
            slidingWindow.add(permitsInOldestSegment + leftOverPermits)
        } else {
            // add a new segment with what is left of the total permits
            slidingWindow.add(leftOverPermits)
        }
    }

    // within the same segment
    override fun updateSemaphoreStatePermits(updateFunction: (Int) -> Int) {
        // update total permits
        semaphoreState.setPermits(updateFunction)
        // update segment permits
        val permitsToAdd = updateFunction(0)
        // doesn't need to coerce-in as it is guaranteed to be within 0-totalPermits at this point
        slidingWindow[currentSegment] = (slidingWindow[currentSegment] + permitsToAdd).coerceIn(
            minimumValue = 0,
            maximumValue = algorithm.totalPermits
        )
    }

}
