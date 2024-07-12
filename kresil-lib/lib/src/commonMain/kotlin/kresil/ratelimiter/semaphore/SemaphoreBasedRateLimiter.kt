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
import kresil.core.semaphore.SuspendableSemaphore
import kresil.core.timemark.getCurrentTimeMark
import kresil.core.timemark.hasExceededDuration
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kresil.ratelimiter.semaphore.request.RateLimitedRequest
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.coroutines.Continuation
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.time.Duration

/**
 * A counting suspendable [Semaphore](https://docs.oracle.com/javase%2F8%2Fdocs%2Fapi%2F%2F/java/util/concurrent/Semaphore.html)
 * based implementation of a rate limiter that uses a queue to store the excess requests that are waiting for permits
 * to be available.
 * This implementation behaviour is dependent on the configuration provided (e.g., total permits, queue length, etc.).
 * @param config The configuration for the rate limiter mechanism.
 * @param semaphoreState The state of the semaphore.
 * @throws CancellationException if the coroutine is cancelled while waiting for the permits to be available.
 * @throws RateLimiterRejectedException if the request is rejected due to the queue being full or the acquisition timeout being exceeded.
 * @see SuspendableSemaphore
 */
internal abstract class SemaphoreBasedRateLimiter(
    private val config: RateLimiterConfig,
    private val semaphoreState: SemaphoreState,
    // TODO: add a distributed queue to manage awaiting requests between multiple instances of this rate limiter, and
    //  ensure FIFO order of requests
) : SuspendableSemaphore {

    private companion object {
        val logger = KotlinLogging.logger(
            SemaphoreBasedRateLimiter::class.simpleName ?: error { "Class name is null" }
        )
    }

    private val lock = Mutex()
    private val algorithm = config.algorithm

    // state
    private val permitsInUse
        get() = semaphoreState.permitsInUse
    protected val currentReplenishmentTimeMark
        get() = semaphoreState.replenishmentTimeMark
    private val queue = semaphoreState.queue

    override suspend fun acquire(permits: Int, timeout: Duration) {
        require(permits > 0) { "Cannot acquire a non-positive number of permits" }
        timeout.requireNonNegative("Acquisition timeout")
        logger.info { "Trying to acquire ($permits) permits" }
        lock.lock()
        logger.info { "Lock acquired" }
        if (hasExceededDuration(currentReplenishmentTimeMark, config.algorithm.replenishmentPeriod)) {
            logger.info { "Current period has expired, replenishing the semaphore state" }
            replenishSemaphoreState()
            semaphoreState.setReplenishmentTimeMark(getCurrentTimeMark())
        }
        if (isRateLimited(permits)) {
            logger.info { "Not enough permits available, rate limiting the request" }
            var localRequestNode: Node<RateLimitedRequest>? = null
            // 1. enqueue request
            if (queue.size < algorithm.queueLength) {
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
                        handleExceptionAndCleanup(localRequestNode, createRateLimitedException(permits))
                    }
                } catch (ex: CancellationException) {
                    handleExceptionAndCleanup(localRequestNode, ex)
                }
            } else {
                // 2. reject request because queue is full or length is zero
                logger.info {
                    "Rejecting request because ${
                        if (algorithm.queueLength == 0) "queue length is zero"
                        else "queue with length (${algorithm.queueLength}) is full"
                    }"
                }
                lock.unlock()
                config.onRejected(createRateLimitedException(permits))
            }
        } else {
            updateSemaphoreStatePermits { it + permits }
            logger.info { "Acquired ($permits) permits. Total (${algorithm.totalPermits}). In use: ($permitsInUse)" }
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
            updateSemaphoreStatePermits { it - permits }
            logger.info { "Released ($permits) permits. Total (${algorithm.totalPermits}). In use: ($permitsInUse)" }
            var permitsLeft = permits
            while (queue.headCondition { it.permits <= permitsLeft }) {
                logger.info { "Raised conditions to resume a pending request" }
                val pendingRequestNode = queue.dequeue()
                val pendingRequest = pendingRequestNode.value
                pendingRequest.canResume = true
                updateSemaphoreStatePermits { it + pendingRequest.permits }
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

    /**
     * Creates a [RateLimiterRejectedException] with the appropriate retry duration.
     * @param permits The number of permits requested.
     */
    private fun createRateLimitedException(permits: Int) = RateLimiterRejectedException(
        retryAfter = calculateRetryDuration(permits)
    )

    /**
     * Calculates the duration after which the request can be retried.
     * The retry duration depends on the rate limiting algorithm, the current state of the semaphore and the
     * number of permits requested.
     * For example, a token bucket algorithm may return a retry duration based on the time left
     * until the requested permits are available (which may not happen in the next replenishment period).
     * @param permits The number of permits requested.
     */
    protected abstract fun calculateRetryDuration(permits: Int): Duration

    /**
     * Updates the number of permits in the semaphore state.
     * @param updateFunction A function that updates the number of permits considering the current value.
     */
    protected open fun updateSemaphoreStatePermits(updateFunction: (Int) -> Int) {
        semaphoreState.setPermits(updateFunction)
    }

    /**
     * Depending on the rate limiting algorithm, this function is responsible for replenishing the semaphore state.
     * For example, a fixed window counter algorithm may reset the permits to zero at the end of the window.
     * And a token bucket algorithm may refill the bucket with tokens at a constant rate.
     */
    protected abstract suspend fun replenishSemaphoreState()

    /**
     * Determines if the request should be rate limited based on the current state of the semaphore.
     * @param permits The number of permits requested.
     */
    private fun isRateLimited(permits: Int): Boolean = permitsInUse + permits > algorithm.totalPermits

    /**
     * Deals with the caught exception while maintaining the integrity of the semaphore state.
     */
    private suspend fun handleExceptionAndCleanup(
        observedRequestNode: Node<RateLimitedRequest>?,
        exception: Exception,
    ): Unit = lock.withLock {
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
