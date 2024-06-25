package kresil.ratelimiter.semaphore

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kresil.core.delay.requireNonNegative
import kresil.core.timemark.getCurrentTimeMark
import kresil.core.timemark.hasExceededDuration
import kresil.core.utils.NodeLinkedList
import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kotlin.coroutines.resume
import kotlin.time.Duration

internal class Request(
    val permits: Int,
    val continuation: CancellableContinuation<Unit>,
    var canResume: Boolean = false,
)

internal class InMemorySemaphore(
    private val config: RateLimiterConfig,
    private val queue: NodeLinkedList<Request>
) : Semaphore {

    private var permitsInUse = 0
    private val lock = Mutex()

    private fun isRateLimited(permits: Int): Boolean {
        return permitsInUse + permits > config.totalPermits
    }

    private var currentRefreshTimeMark = getCurrentTimeMark()

    override suspend fun acquire(permits: Int, timeout: Duration): Unit {
        require(permits > 0) { "Cannot acquire a non-positive number of permits" }
        timeout.requireNonNegative("Timeout")

        lock.lock()
        if (hasExceededDuration(currentRefreshTimeMark, config.refreshPeriod)) {
            permitsInUse = 0
            currentRefreshTimeMark = getCurrentTimeMark()
        }

        if (isRateLimited(permits)) {
            var localRequestNode: NodeLinkedList.Node<Request>? = null
            // 1. enqueue request
            var wasUnlocked = false
            if (queue.size < config.queueLength) {
                try {
                    withTimeoutOrNull(timeout) {
                        suspendCancellableCoroutine { continuation ->
                            val request = Request(permits, continuation)
                            localRequestNode = queue.enqueue(request)
                            lock.unlock() // suspend with lock released
                            wasUnlocked = true
                        }
                    } ?: handleExceptionAndCleanup(localRequestNode, RateLimiterRejectedException())
                } catch (ex: CancellationException) {
                    // TODO: confirm that coroutine could be cancelled before suspending inside
                    //  suspendCancellableCoroutine as mutex is not reentrant
                    if (!wasUnlocked) {
                        lock.unlock()
                    }
                    handleExceptionAndCleanup(localRequestNode, ex)
                }
            } else {
                // 2. reject request because queue is full
                lock.unlock()
                config.onRejected(RateLimiterRejectedException())
            }
        } else {
            permitsInUse += permits
            lock.unlock()
        }
    }

    override suspend fun release(permits: Int): Unit {
        require(permits > 0) { "Cannot release a non-positive number of permits" }
        val listOfRequestToResume = mutableListOf<Request>()
        // check if the permit release can be used to resume any pending requests
        lock.withLock {
            permitsInUse -= permits
            var permitsLeft = permits
            while (queue.headCondition { it.permits <= permitsLeft }) {
                val pendingRequestNode = queue.pull()
                queue.remove(pendingRequestNode)
                val pendingRequest = pendingRequestNode.value
                pendingRequest.canResume = true
                permitsInUse += pendingRequest.permits
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
        observedRequestNode: NodeLinkedList.Node<Request>?,
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
