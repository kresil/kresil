package kresil.ratelimiter

import kresil.core.events.FlowEventListenerImpl
import kresil.core.oper.Supplier
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.config.defaultRateLimiterConfig
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.event.RateLimiterEvent
import kresil.ratelimiter.semaphore.InMemoryKeyBasedSemaphore
import kresil.ratelimiter.semaphore.KeyBasedSemaphore
import kotlin.time.Duration

/**
* The [Rate Limiter](https://learn.microsoft.com/en-us/azure/architecture/patterns/rate-limiting) is a **proactive** resilience mechanism
that can be used to limit the number of requests that can be made to a system component, thereby controlling the consumption of resources and protecting the system from overloading.
A rate limiter is initialized with a configuration that, through pre-configured policies, defines its behaviour.
 * @param config The configuration for the rate limiter mechanism.
 * @see [rateLimiterConfig]
 */
class RateLimiter<Key>(
    val config: RateLimiterConfig = defaultRateLimiterConfig(),
    private val semaphore: KeyBasedSemaphore<Key> = InMemoryKeyBasedSemaphore(config)
) : FlowEventListenerImpl<RateLimiterEvent>(), KeyBasedSemaphore<Key> by semaphore {

    /**
     * Decorates a suspending function with rate limiting.
     * @param key The key to use for rate limiting.
     * @param permits The number of permits required to execute the function.
     * @param timeout The duration to wait for permits to be available.
     * @param block The suspending function to decorate.
     * @return The result of the suspending function.
     */
    suspend fun <R> call(
        key: Key,
        permits: Int,
        timeout: Duration,
        block: Supplier<R>
    ): R {
        acquire(key, permits, timeout)
        try {
            return block()
        } finally {
            release(key, permits)
        }
    }
}
