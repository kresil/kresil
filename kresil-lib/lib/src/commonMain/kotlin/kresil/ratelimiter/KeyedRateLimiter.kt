package kresil.ratelimiter

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kresil.core.oper.Supplier
import kresil.core.queue.Queue
import kresil.core.utils.CircularDoublyLinkedList
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.config.defaultRateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kresil.ratelimiter.semaphore.queue.RateLimitedRequest
import kresil.ratelimiter.semaphore.state.InMemorySemaphoreState
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * Manages multiple [RateLimiter] instances based on a [Key], allowing
 * rate limiting based on different keys.
 *
 * In this implementation, each rate limiter has its **own semaphore state** but the queue,
 * used to store excess requests, is **shared** across all rate limiters.
 * The queue (to use when each rate limiter is exceeded) should be shared among all rate limiters to ensure that,
 * in a node-balanced environment, nodes do not develop an affinity for processing specific requests.
 * This queue should be thread-safe as it is not protected by a rate limiter's semaphore.
 *```
 *              +------------------------------------+
 *              |       +------+      +------+       |
 *              |   |-> |  K1  | ---> |  RL  |---+   |
 *              |   |   +------+      +------+   |   |
 *              |   | > |  K2  |                 |   |
 *    Request   |   |   +------+      +------+   |   |
 *  ----------> + --|-> |  K3  | ---> |  RL  |---|   |
 *              |   |   +------+      +------+   |   |
 *              |   |-> |  K4  | ---> |  RL  |---|   |
 *              |   |   +------+      +------+   |   |
 *              |   |-> |  K5  |                 |   |
 *              |       +------+    +--------+   |   |
 *              |                   | Shared |<--+   |
 *              |                   | Queue  |-->+   |
 *              |                   +--------+       |
 *              +------------------------------------+
 *```
 * @param Key The type of key used to identify different rate limiters.
 * @param config The configuration defining the behavior of each rate limiter.
 * @param semaphoreStateFactory A factory function to create semaphore states for rate limiters.
 * Defaults to creating an **in-memory** semaphore state for each rate limiter.
 * @param sharedQueue The queue used to store excess requests across all rate limiters.
 * Must be thread-safe as it is not protected by a rate limiter's semaphore.
 * Defaults to an **in-memory** [CircularDoublyLinkedList].
 * // TODO introduce a concurrent queue implementation
 */
class KeyedRateLimiter<Key>(
    private val config: RateLimiterConfig = defaultRateLimiterConfig(),
    private val semaphoreStateFactory: () -> SemaphoreState = { InMemorySemaphoreState() },
    private val sharedQueue: Queue<RateLimitedRequest> = CircularDoublyLinkedList(),
) { // TODO: what events could be emitted here?

    private val limiters = mutableMapOf<Key, RateLimiter>()
    private val lock = Mutex()

    private suspend fun getOrCreateRateLimiter(key: Key): RateLimiter = lock.withLock {
        limiters.getOrPut(key) {
            RateLimiter(config, semaphoreStateFactory(), sharedQueue)
        }
    }

    /**
     * Decorates a [Supplier] with a rate limiter based on the provided key.
     * @param key The key used to identify the rate limiter.
     * @param permits The number of permits required to execute the function.
     * @param timeout The duration to wait for permits to be available.
     * @param block The suspending function to decorate.
     * @throws CancellationException if the coroutine is cancelled while waiting for the permits to be available.
     * @throws RateLimiterRejectedException if the request is rejected due to the queue being full or the timeout for acquiring permits has expired.
     */
    @Throws(RateLimiterRejectedException::class, CancellationException::class)
    suspend fun <R> call(
        key: Key,
        permits: Int = 1,
        timeout: Duration = config.baseTimeoutDuration,
        block: Supplier<R>,
    ): R {
        val rateLimiter = getOrCreateRateLimiter(key)
        return rateLimiter.call(permits, timeout, block)
    }
}
