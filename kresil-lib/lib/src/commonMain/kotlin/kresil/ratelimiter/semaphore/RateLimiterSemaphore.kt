package kresil.ratelimiter.semaphore

import io.github.oshai.kotlinlogging.KotlinLogging
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

    private val logger = KotlinLogging.logger(
        RateLimiterSemaphore::class.simpleName ?: error { "Class name is null" }
    )

    private val lock = Mutex()
    private val permitsInUse
        get() = semaphoreState.permitsInUse
    private val currentRefreshTimeMark
        get() = semaphoreState.refreshTimeMark

    private fun isRateLimited(permits: Int): Boolean = permitsInUse + permits > config.totalPermits

    // TODO: should be a snapshot of the current refresh time mark inside or outside the lock possession?
    //  this is because the cycle could have been refreshed while waiting for the lock
    private fun rateLimitedException() = RateLimiterRejectedException(
        retryAfter = getRemainingDuration(currentRefreshTimeMark, config.refreshPeriod)
    )

    override suspend fun acquire(permits: Int, timeout: Duration) {
        require(permits > 0) { "Cannot acquire a non-positive number of permits" }
        timeout.requireNonNegative("Acquisition timeout")
        lock.lock()
        logger.info { "Trying to acquire ($permits) permits" }
        if (hasExceededDuration(currentRefreshTimeMark, config.refreshPeriod)) {
            logger.info { "Refreshing the semaphore state" }
            // TODO: this set operations could be retrieving data from a database
            //  and potentially holding the lock for a long time
            semaphoreState.setPermits { 0 }
            semaphoreState.setRefreshTimeMark(getCurrentTimeMark())
        }
        if (isRateLimited(permits)) {
            logger.info { "Not enough permits available, rate limiting the request" }
            var localRequestNode: Node<RateLimitedRequest>? = null
            // 1. enqueue request
            if (queue.size < config.queueLength) {
                lock.unlock()
                try {
                    withTimeoutOrNull(timeout) {
                        CompletableDeferred<Unit>().apply {
                            val continuation = Continuation(currentCoroutineContext()) { completeWith(it) }
                            val request = RateLimitedRequest(permits, continuation)
                            lock.withLock {
                                // TODO: shouldn't suspend in lock possession
                                localRequestNode = queue.enqueue(request)
                                logger.info { "Request enqueued" }
                            }
                            logger.info { "Request coroutine was suspended" }
                        }.await()
                        logger.info { "Request coroutine was resumed successfully" }
                    } ?: run {
                        logger.info { "Request timed out" }
                        handleExceptionAndCleanup(localRequestNode, rateLimitedException())
                    }
                } catch (ex: CancellationException) {
                    handleExceptionAndCleanup(localRequestNode, ex)
                }
            } else {
                // 2. reject request because queue is full or length is zero
                logger.info {
                    "Rejecting request because ${
                        if (config.queueLength == 0) "queue length is zero"
                        else "queue with length (${config.queueLength}) is full"
                    }"
                }
                lock.unlock()
                config.onRejected(rateLimitedException())
            }
        } else {
            semaphoreState.setPermits { it + permits }
            logger.info { "Acquired ($permits) permits. Total (${config.totalPermits}). In use: $permitsInUse" }
            lock.unlock()
        }
    }

    override suspend fun release(permits: Int) {
        require(permits > 0) { "Cannot release a non-positive number of permits" }
        logger.info { "Trying to release ($permits) permits" }
        val listOfRequestToResume = mutableListOf<RateLimitedRequest>()
        // check if the permit release can be used to resume any pending requests
        lock.withLock {
            require(permitsInUse - permits >= 0) { "Cannot release more permits than are in use" }
            semaphoreState.setPermits { it - permits }
            logger.info { "Released ($permits) permits. Total (${config.totalPermits}). In use: $permitsInUse" }
            var permitsLeft = permits
            while (queue.headCondition { it.permits <= permitsLeft }) {
                logger.info { "Raised conditions to resume a pending request" }
                val pendingRequestNode = queue.dequeue()
                val pendingRequest = pendingRequestNode.value
                pendingRequest.canResume = true
                semaphoreState.setPermits { it + pendingRequest.permits }
                listOfRequestToResume.add(pendingRequest)
                permitsLeft -= pendingRequest.permits
            }
        }
        // resume pending requests (coroutines) without holding the lock
        listOfRequestToResume.forEach {
            logger.info { "Resuming request with (${it.permits}) permits" }
            it.continuation.resume(Unit)
        }
    }

    private suspend fun handleExceptionAndCleanup(
        observedRequestNode: Node<RateLimitedRequest>?,
        exception: Exception,
    ): Unit = lock.withLock {
        println(exception)
        observedRequestNode?.let {
            val request = it.value
            if (!request.canResume) {
                logger.info { "Removing request from queue" }
                queue.remove(it)
            }
        }
        config.onRejected(exception)
    }
}
