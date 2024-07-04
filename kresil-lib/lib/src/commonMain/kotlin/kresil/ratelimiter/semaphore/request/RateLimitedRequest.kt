package kresil.ratelimiter.semaphore.request

import kotlin.coroutines.Continuation

/**
 * Represents a request that was rate-limited and enqueued in a queue.
 * @param permits The number of permits required by the request.
 * @param continuation The continuation that will be resumed when the request is granted.
 * @param canResume A flag indicating whether the request was granted and internal state consistency was maintained.
 * Therefore, the coroutine can be safely resumed.
 */
internal class RateLimitedRequest(
    val permits: Int,
    val continuation: Continuation<Unit>,
    var canResume: Boolean = false,
)
