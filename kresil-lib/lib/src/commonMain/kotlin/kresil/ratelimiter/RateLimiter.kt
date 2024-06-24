package kresil.ratelimiter

import kresil.core.events.FlowEventListenerImpl
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.config.defaultRateLimiterConfig
import kresil.ratelimiter.event.RateLimiterEvent

/**
* The [Rate Limiter](https://learn.microsoft.com/en-us/azure/architecture/patterns/rate-limiting) is a **proactive** resilience mechanism
that can be used to limit the number of requests that can be made to a system component, thereby controlling the consumption of resources and protecting the system from overloading.
A rate limiter is initialized with a configuration that, through pre-configured policies, defines its behaviour.
 * @param config The configuration for the rate limiter mechanism.
 * @see [rateLimiterConfig]
 */
class RateLimiter(
    val config: RateLimiterConfig = defaultRateLimiterConfig(),
) : FlowEventListenerImpl<RateLimiterEvent>() {
    // TODO()
}
