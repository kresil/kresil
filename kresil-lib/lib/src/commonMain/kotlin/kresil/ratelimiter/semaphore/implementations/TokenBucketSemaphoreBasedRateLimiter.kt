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
 * A counting suspendable [Semaphore](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Semaphore.html)
 * based implementation of a rate limiter that uses the Token Bucket algorithm to control the number of permits available for requests.
 * The Token Bucket algorithm allows for a smoother distribution of requests over time, compared to the fixed window counter algorithm.
 * Tokens are added to the bucket at a constant rate, up to a maximum capacity, and each request consumes a certain number of tokens.
 * If there are insufficient tokens for a request, the request is either queued or rejected based on the configuration.
 *
 * ##### Considerations
 * The Token Bucket algorithm helps prevent bursts of requests and provides a more even distribution of traffic.
 * It can be more effective than the fixed window counter algorithm in scenarios with fluctuating traffic patterns.
 * @param config The configuration for the rate limiter mechanism.
 * @param semaphoreState The state of the semaphore which includes an internal in-memory queue to store the excess requests.
 * @throws CancellationException if the coroutine is cancelled while waiting for the permits to be available.
 * @throws RateLimiterRejectedException if the request is rejected due to the queue being full or the acquisition timeout being exceeded.
 * @see SuspendableSemaphore
 */
internal class TokenBucketSemaphoreBasedRateLimiter(
    private val config: RateLimiterConfig,
    private val semaphoreState: SemaphoreState,
) : SemaphoreBasedRateLimiter(config, semaphoreState) {

    override fun calculateRetryDuration(permits: Int): Duration {
        // knowing that each replenishment period adds 1 token to the bucket (at a constant rate)
        val durationUntilNextReplinish =
            getRemainingDuration(currentReplenishmentTimeMark, config.algorithm.replenishmentPeriod)
        return durationUntilNextReplinish * permits
    }

    override suspend fun replenishSemaphoreState() {
        // get how many times the replenishment period has passed
        val tokensToAdd = (currentReplenishmentTimeMark.elapsedNow() / config.algorithm.replenishmentPeriod).toInt()
        if (semaphoreState.permitsInUse < config.algorithm.totalPermits) {
            semaphoreState.setPermits { (it + tokensToAdd).coerceAtMost(config.algorithm.totalPermits) }
        }
    }
}
