package kresil.ratelimiter

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kresil.core.oper.Supplier
import kresil.core.utils.NodeLinkedList
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.config.defaultRateLimiterConfig
import kresil.ratelimiter.semaphore.InMemorySemaphore
import kresil.ratelimiter.semaphore.Request
import kotlin.time.Duration

class KeyedRateLimiter<Key>(
    private val config: RateLimiterConfig = defaultRateLimiterConfig(),
) { // TODO: what events could be emitted here?
    private val limiters = mutableMapOf<Key, RateLimiter>()
    private val lock = Mutex()
    private val sharedQueue = NodeLinkedList<Request>()

    private suspend fun getOrCreateRateLimiter(key: Key): RateLimiter = lock.withLock {
        limiters.getOrPut(key) {
            RateLimiter(config, InMemorySemaphore(config, sharedQueue))
        }
    }

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
