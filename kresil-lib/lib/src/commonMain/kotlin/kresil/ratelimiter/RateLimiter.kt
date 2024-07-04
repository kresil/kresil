package kresil.ratelimiter

import kresil.core.events.FlowEventListenerImpl
import kresil.core.oper.Supplier
import kresil.core.queue.Queue
import kresil.core.semaphore.SuspendableSemaphore
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.FixedWindowCounter
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.config.defaultRateLimiterConfig
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.event.RateLimiterEvent
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kresil.ratelimiter.semaphore.RateLimiterSemaphore
import kresil.ratelimiter.semaphore.implementations.FixedWindowCounterSemaphore
import kresil.ratelimiter.semaphore.state.InMemorySemaphoreState
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * The [Rate Limiter](https://learn.microsoft.com/en-us/azure/architecture/patterns/rate-limiting) is a **proactive**
 * resilience mechanism that can be used to limit the number of requests that can be made to a system component,
 * thereby controlling the consumption of resources and protecting the system from overloading.
 * A rate limiter is initialized with a configuration that, through pre-configured policies, defines its behaviour.
 *
 * In this implementation, the rate limiter uses a **counting semaphore** synchronization primitive to control the number
 * of permits available for requests and **queue** to store the excess requests that are waiting for permits to be available.
 * Since a rate limiter can be used in distributed architectures, the semaphore and the queue state
 * can be stored in a shared data store, such as a database,
 * by implementing the [SemaphoreState] and [Queue] interfaces, respectively.
 * // TODO: revisit this statement after distributed queue implementation
 *
 * **Note**: How long a request holds n permits is determined by the duration of the suspending
 * function that the rate limiter decorates,
 * and is therefore is not controlled by the rate limiter itself.
 * It is the responsibility of the caller to ensure proper timeout handling to avoid a request
 * holding permits indefinitely.
 *
 * @param config The configuration for the rate limiter mechanism.
 * @param semaphoreState The state of the semaphore. Defaults to an in-memory semaphore state.
 * @see [rateLimiterConfig]
 * @see [KeyedRateLimiter]
 */
class RateLimiter(
    val config: RateLimiterConfig = defaultRateLimiterConfig(),
    semaphoreState: SemaphoreState = InMemorySemaphoreState(),
) : FlowEventListenerImpl<RateLimiterEvent>(), SuspendableSemaphore {

    private val semaphore: RateLimiterSemaphore = when (config.algorithm) {
        is FixedWindowCounter -> FixedWindowCounterSemaphore(config, semaphoreState)
        else -> error("Unsupported rate limiting algorithm: ${config.algorithm}")
    }

    /**
     * Decorates a [Supplier] with this rate limiter.
     * @param permits The number of permits required to execute the function.
     * @param timeout The duration to wait for permits to be available.
     * @param block The suspending function to decorate.
     * @throws CancellationException if the coroutine is cancelled while waiting for the permits to be available.
     * @throws RateLimiterRejectedException if the request is rejected due to the queue being full or the timeout for acquiring permits has expired.
     */
    @Throws(RateLimiterRejectedException::class, CancellationException::class)
    suspend inline fun <R> call(
        permits: Int = 1,
        timeout: Duration = config.baseTimeoutDuration,
        block: Supplier<R>,
    ): R {
        acquire(permits, timeout)
        return try {
            block()
        } finally {
            release(permits)
        }
    }

    override suspend fun acquire(permits: Int, timeout: Duration): Unit =
        semaphore.acquire(permits, timeout)

    override suspend fun release(permits: Int): Unit =
        semaphore.release(permits)
}
