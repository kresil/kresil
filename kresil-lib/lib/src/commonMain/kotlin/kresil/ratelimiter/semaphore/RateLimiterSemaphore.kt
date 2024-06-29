package kresil.ratelimiter.semaphore

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kresil.core.delay.requireNonNegative
import kresil.core.queue.Node
import kresil.core.queue.Queue
import kresil.core.semaphore.SuspendableSemaphore
import kresil.core.timemark.getCurrentTimeMark
import kresil.core.timemark.getRemainingDuration
import kresil.core.timemark.hasExceededDuration
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kresil.ratelimiter.semaphore.queue.RateLimitedRequest
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.coroutines.Continuation
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.time.Duration

/**
 * A counting suspendable [Semaphore](https://docs.oracle.com/javase%2F8%2Fdocs%2Fapi%2F%2F/java/util/concurrent/Semaphore.html)
 * implementation of a rate limiter that uses a queue to store the excess requests that are waiting for permits
 * to be available.
 * This implementation behaviour is dependent on the configuration provided (e.g., total permits, queue length, etc.).
 * @param config The configuration for the rate limiter mechanism.
 * @param semaphoreState The state of the semaphore.
 * @param queue The queue to place the *excess* requests.
 * @throws CancellationException if the coroutine is cancelled while waiting for the permits to be available.
 * @throws RateLimiterRejectedException if the request is rejected due to the queue being full or the acquisition timeout being exceeded.
 * @see SuspendableSemaphore
 */
internal class RateLimiterSemaphore(
    private val config: RateLimiterConfig,
    private val semaphoreState: SemaphoreState,
    private val queue: Queue<RateLimitedRequest>,
) : SuspendableSemaphore {

    private val lock = Mutex()
    private val permitsInUse
        get() = semaphoreState.permitsInUse
    private val currentRefreshTimeMark
        get() = semaphoreState.refreshTimeMark

    private fun isRateLimited(permits: Int): Boolean = permitsInUse + permits > config.totalPermits

    // TODO: should be a snapshot of the current refresh time mark? or in lock possession?
    //  this is because the cycle could have been refreshed while waiting for the lock
    private fun rateLimitedException() = RateLimiterRejectedException(
        retryAfter = getRemainingDuration(currentRefreshTimeMark, config.refreshPeriod)
    )

    override suspend fun acquire(permits: Int, timeout: Duration) {
        require(permits > 0) { "Cannot acquire a non-positive number of permits" }
        timeout.requireNonNegative("Acquisition timeout")
        lock.lock()
        if (hasExceededDuration(currentRefreshTimeMark, config.refreshPeriod)) {
            // TODO: this set operations could be retrieving data from a database
            //  and potentially holding the lock for a long time
            semaphoreState.setPermits { 0 }
            semaphoreState.setRefreshTimeMark(getCurrentTimeMark())
        }
        println(isRateLimited(permits))
        if (isRateLimited(permits)) {
            var localRequestNode: Node<RateLimitedRequest>? = null
            // 1. enqueue request
            if (queue.size < config.queueLength) {
                lock.unlock()
                try {
                    withTimeoutOrNull(timeout) {
                        CompletableDeferred<Unit>().apply {
                            val continuation = Continuation(currentCoroutineContext()) { completeWith(it) }
                            val request = RateLimitedRequest(permits, continuation)
                            // TODO: shouldn't suspend in lock possession
                            lock.withLock { localRequestNode = queue.enqueue(request) }
                        }.await()
                    } ?: handleExceptionAndCleanup(localRequestNode, rateLimitedException())
                } catch (ex: CancellationException) {
                    handleExceptionAndCleanup(localRequestNode, ex)
                }
            } else {
                // 2. reject request because queue is full or length is zero
                lock.unlock()
                config.onRejected(rateLimitedException())
            }
        } else {
            semaphoreState.setPermits { it + permits }
            lock.unlock()
        }
    }

    override suspend fun release(permits: Int) {
        require(permits > 0) { "Cannot release a non-positive number of permits" }
        val listOfRequestToResume = mutableListOf<RateLimitedRequest>()
        // check if the permit release can be used to resume any pending requests
        lock.withLock {
            semaphoreState.setPermits { it - permits }
            var permitsLeft = permits
            while (queue.headCondition { it.permits <= permitsLeft }) {
                val pendingRequestNode = queue.dequeue()
                queue.remove(pendingRequestNode)
                val pendingRequest = pendingRequestNode.value
                pendingRequest.canResume = true
                semaphoreState.setPermits { it + pendingRequest.permits }
                listOfRequestToResume.add(pendingRequest)
                permitsLeft -= pendingRequest.permits
            }
        }
        // resume pending requests (coroutines) without holding the lock
        listOfRequestToResume.forEach {
            it.continuation.resume(Unit)
        }
    }

    private suspend fun handleExceptionAndCleanup(
        observedRequestNode: Node<RateLimitedRequest>?,
        exception: Exception,
    ): Unit = lock.withLock {
        observedRequestNode?.let {
            val request = it.value
            if (!request.canResume) {
                queue.remove(it)
            }
        }
        config.onRejected(exception)
    }
}
