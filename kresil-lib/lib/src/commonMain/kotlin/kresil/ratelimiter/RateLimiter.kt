package kresil.ratelimiter

import kresil.core.events.FlowEventListenerImpl
import kresil.core.oper.Supplier
import kresil.core.utils.NodeLinkedList
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.config.defaultRateLimiterConfig
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.event.RateLimiterEvent
import kresil.ratelimiter.semaphore.InMemorySemaphore
import kresil.ratelimiter.semaphore.Semaphore
import kotlin.time.Duration

/**
* The [Rate Limiter](https://learn.microsoft.com/en-us/azure/architecture/patterns/rate-limiting) is a **proactive** resilience mechanism
that can be used to limit the number of requests that can be made to a system component, thereby controlling the consumption of resources and protecting the system from overloading.
A rate limiter is initialized with a configuration that, through pre-configured policies, defines its behaviour.
 * @param config The configuration for the rate limiter mechanism.
 * @see [rateLimiterConfig]
 */
class RateLimiter(
    val config: RateLimiterConfig = defaultRateLimiterConfig(),
    semaphore: Semaphore = InMemorySemaphore(config, NodeLinkedList())
) : FlowEventListenerImpl<RateLimiterEvent>(), Semaphore by semaphore {

    /**
     * Decorates a [Supplier] with this rate limiter.
     * @param permits The number of permits required to execute the function.
     * @param timeout The duration to wait for permits to be available.
     * @param block The suspending function to decorate.
     * @return The result of the suspending function.
     */
    suspend fun <R> call(
        permits: Int = 1,
        timeout: Duration = config.baseTimeoutDuration,
        block: Supplier<R>
    ): R {
        acquire(permits, timeout)
        return try {
            block()
        } finally {
            release(permits)
        }
    }
}
