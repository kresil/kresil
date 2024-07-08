package kresil.ratelimiter

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kresil.core.oper.Supplier
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.config.defaultRateLimiterConfig
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kresil.ratelimiter.semaphore.state.InMemorySemaphoreState
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * Manages multiple [RateLimiter] instances based on a [Key], allowing
 * rate limiting based on different keys.
 * This is useful when you want to rate limit different resources or operations separately.
 *```
 *              +--------------------------------+
 *              |       +------+      +------+   |
 *              |   |-> |  K1  | ---> |  RL  |   |
 *              |   |   +------+      +------+   |
 *              |   | > |  K2  |                 |
 *    Request   |   |   +------+      +------+   |
 *  ----------> + --|-> |  K3  | ---> |  RL  |   |
 *              |   |   +------+      +------+   |
 *              |   |-> |  K4  | ---> |  RL  |   |
 *              |   |   +------+      +------+   |
 *              |   |-> |  K5  |                 |
 *              |       +------+                 |
 *              +--------------------------------+
 *```
 * Since each rate limiter can be used in distributed architectures, the semaphore state can be stored
 * in a shared data store, such as a database, by implementing the [SemaphoreState] interface.
 * @param Key The type of key used to identify different rate limiters.
 * @param config The configuration defining the behavior of each rate limiter.
 * @param semaphoreStateFactory A factory function to create semaphore states for rate limiters.
 * Defaults to creating an **in-memory** semaphore state for each rate limiter.
 * @see rateLimiterConfig
 * @see RateLimiter
 */
@OptIn(ExperimentalStdlibApi::class)
class KeyedRateLimiter<Key>(
    val config: RateLimiterConfig = defaultRateLimiterConfig(),
    private val semaphoreStateFactory: () -> SemaphoreState = { InMemorySemaphoreState() },
) : AutoCloseable {

    @PublishedApi
    internal val wasDisposed = atomic(false)
    private val limiters = mutableMapOf<Key, RateLimiter>()
    private val lock = Mutex()

    /**
     * Gets the rate limiter based on the provided key, creating a new one if it doesn't exist.
     * @param key The key used to identify the rate limiter.
     */
    suspend fun getRateLimiter(key: Key): RateLimiter = lock.withLock {
        limiters.getOrPut(key) {
            RateLimiter(config, semaphoreStateFactory())
        }
    }

    /**
     * Removes the rate limiter based on the provided key.
     * Before removing the rate limiter, it is closed to release any resources it may hold.
     */
    suspend fun removeRateLimiter(key: Key) {
        lock.withLock { limiters.remove(key) }?.use { }
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
    suspend inline fun <R> call(
        key: Key,
        permits: Int = 1,
        timeout: Duration = config.baseTimeoutDuration,
        block: Supplier<R>,
    ): R {
        check(!wasDisposed.value) { "Rate limiter has been disposed" }
        val rateLimiter = getRateLimiter(key)
        return rateLimiter.call(permits, timeout, block)
    }

    override fun close() {
        if (wasDisposed.value) return
        CoroutineScope(Dispatchers.Default).launch {
            val rateLimiters = lock.withLock {
                wasDisposed.value = true
                limiters.values
            }
            var exception: Exception? = null
            rateLimiters.forEach {
                try {
                    it.close()
                } catch (e: Exception) {
                    if (exception == null) {
                        exception = e
                    } else {
                        exception?.addSuppressed(e)
                    }
                }
            }
            exception?.let { throw it }
        }
    }

}
