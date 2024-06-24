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

class Request<T>(
    val key: T, // TODO: is there any reason to keep the key in the request?
    val permits: Int,
    val continuation: CancellableContinuation<Unit>,
    var canResume: Boolean = false,
)

class InMemoryKeyBasedSemaphore<T>(
    private val config: RateLimiterConfig,
) : KeyBasedSemaphore<T> {

    private val queue = NodeLinkedList<Request<T>>() // TODO: check ArrayDeque
    private val permitsPerKeyMap = mutableMapOf<T, Int>()
    private val lock = Mutex()
    private val hasExceededTotalPermits: Boolean
        get() = permitsPerKeyMap.values.sum() >= config.totalPermits

    private fun isRateLimited(permitsForKey: Int): Boolean {
        return permitsForKey <= config.maxPermitsPerKey || hasExceededTotalPermits
    }

    private var currentRefreshTimeMark = getCurrentTimeMark()

    // TODO: mention that this method is cancellation aware
    override suspend fun acquire(key: T, permits: Int, timeout: Duration?): Unit {
        require(permits > 0) { "Cannot acquire a non-positive number of permits" }
        timeout?.requireNonNegative("Timeout")
        // TODO: implement value classes for permits and timeout
        lock.lock()
        // check if this acquisition is in a new refresh period
        if (hasExceededDuration(currentRefreshTimeMark, config.refreshPeriod)) {
            permitsPerKeyMap.clear()
            currentRefreshTimeMark = getCurrentTimeMark()
        }
        // check if key is already in the map
        val permitsForKey = (permitsPerKeyMap[key] ?: 0) + permits
        // check if the key has exceeded the maximum number of permits per key and total permits overall
        if (isRateLimited(permitsForKey)) {
            // check if the queue has reached its maximum length
            // TODO: check if more than n requests are in the queue for the same key
            var localRequestNode: NodeLinkedList.Node<Request<T>>? = null
            if (queue.size < config.queueLength) {
                // enqueue the request and wait for the required permits to be available in the specified timeout
                try {
                    withTimeoutOrNull(timeout ?: config.baseTimeoutDuration) {
                        suspendCancellableCoroutine { continuation ->
                            val request: Request<T> = Request(key, permits, continuation)
                            localRequestNode = queue.enqueue(request)
                            lock.unlock() // unlock the lock before suspending
                        }
                    } ?: handleExceptionAndCleanup(localRequestNode, RateLimiterRejectedException())
                } catch (ex: CancellationException) {
                    handleExceptionAndCleanup(localRequestNode, ex)
                }
            } else {
                lock.unlock()
                // reject the request
                config.onRejected(RateLimiterRejectedException())
            }
        } else {
            permitsPerKeyMap[key] = permitsForKey
            lock.unlock()
        }
    }

    override suspend fun release(key: T, permits: Int): Unit {
        require(permits > 0) { "Cannot release a non-positive number of permits" }
        // get the permits for the key
        val listOfRequestToResume = mutableListOf<Request<T>>()
        lock.withLock {
            val retrievedPermitsForKey = permitsPerKeyMap[key]
            requireNotNull(retrievedPermitsForKey) { "Cannot release permits for a request that did not acquire any" }
            require(retrievedPermitsForKey >= permits) { "Cannot release more permits than acquired" }
            // update the permits for the key
            val updatedPermitsForKey = retrievedPermitsForKey - permits
            permitsPerKeyMap.apply {
                if (updatedPermitsForKey == 0) {
                    remove(key)
                } else {
                    put(key, updatedPermitsForKey)
                }
            }
            // check if releasing permits raise conditions for other requests to be fulfilled
            var permitsLeft = permits
            while (queue.headCondition { it.permits <= permitsLeft }) {
                val pendingRequestNode = queue.pull()
                queue.remove(pendingRequestNode)
                val pendingRequest = pendingRequestNode.value
                pendingRequest.canResume = true
                permitsPerKeyMap[key] = pendingRequest.permits + permitsLeft
                listOfRequestToResume.add(pendingRequest)
                permitsLeft -= pendingRequest.permits
            }
        }
        listOfRequestToResume.forEach {
            it.continuation.resume(Unit)
        }
    }

    private suspend fun handleExceptionAndCleanup(
        observedRequestNode: NodeLinkedList.Node<Request<T>>?,
        exception: Exception,
    ): Unit = lock.withLock {
        if (observedRequestNode != null) {
            val request = observedRequestNode.value
            if (!request.canResume) {
                queue.remove(observedRequestNode)
            }
        }
        config.onRejected(exception)
    }
}
