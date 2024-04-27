package kresil.retry

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kresil.retry.builders.defaultRetryConfig
import kresil.retry.config.RetryConfig
import kresil.retry.context.RetryAsyncContextImpl
import kresil.retry.config.RetryConfigBuilder

/**
 * Represents a retry mechanism that can be used to retry a suspend function.
 *
 * Usage:
 * ```
 * // use default policies
 * val retry = Retry(
 *    retryConfig {
 *         maxAttempts = 3 // initial call + 2 retries
 *         exponentialDelay()
 *    }
 * )
 *
 * // use custom policies
 * val retry = Retry(
 *    retryConfig {
 *       maxAttempts = 5
 *       retryIf { it is NetworkError }
 *       retryOnResultIf { it is "success" }
 *       constantDelay(500.milliseconds)
 *       // customDelay { attempt, lastThrowable -> ... }
 *    }
 * )
 *
 * // execute a suspend function
 * retry.executeSuspendFunction {
 *    // your suspend function
 * }
 *
 * // decorate a suspend function
 * val decoratedFunction = retry.decorateSuspendFunction {
 *   // your suspend function
 * } // and call it later with decoratedFunction()
 *
 * // listen to specific events
 * retry.onRetry { currentAttempt -> println("Attempt: $currentAttempt") }
 * retry.onError { throwable -> println("Error: $throwable") }
 * retry.onIgnoredError { throwable -> println("Ignored error: $throwable") }
 * retry.onSuccess { println("Success") }
 * retry.onCancellation { println("Cancelled") }
 *
 * // listen to all events
 * retry.onEvent { event -> println(event) }
 * ```
 * @param config The configuration for the retry mechanism.
 * @see [RetryConfigBuilder]
 */
class Retry(
    val config: RetryConfig = defaultRetryConfig(),
) {

    // events
    private val eventFlow = MutableSharedFlow<RetryEvent>()
    private val events: Flow<RetryEvent> = eventFlow.asSharedFlow()

    /**
     * Executes a suspend function with this retry mechanism.
     * This function is **cancellation aware** outside of the [block] execution.
     * Even if the coroutine is cancelled, it will still emit the appropriate cancellation event.
     * @param block The suspend function to execute.
     * @see [decorateSuspendFunction]
     */
    suspend fun <T> executeSuspendFunction(block: suspend () -> T) {
        coroutineScope {
            val context = RetryAsyncContextImpl(config, eventFlow)
            val deferred = async {
                while (true) {
                    try {
                        val result: Any? = block()
                        val shouldRetry = context.onResult(result)
                        if (shouldRetry) {
                            context.onRetry()
                            continue
                        }
                        // TODO: should emit retry on success event if no retry was needed to complete?
                        context.onSuccess()
                        break
                    } catch (throwable: Throwable) {
                        context.onError(throwable)
                        context.onRetry()
                    }
                }
            }
            withContext(NonCancellable) {
                context.onCancellation(this, deferred)
            }
        }
    }

    /**
     * Decorates a suspend function with this retry mechanism.
     * The decorated function can be called later and will execute the [block] function.
     * This function is **cancellation aware* outside of the [block] execution.
     * Even if the coroutine is cancelled, it will still emit the appropriate cancellation event.
     * @param block The suspend function to decorate.
     * @return A suspend function that will execute the decorated function.
     * @see [executeSuspendFunction]
     */
    fun <T> decorateSuspendFunction(block: suspend () -> T): suspend () -> Unit = {
        executeSuspendFunction(block)
    }

    /**
     * Executes the given [action] when a retry is attempted.
     * The underlying event is emitted when a retry is attempted, **before entering the delay phase**.
     * @see [onEvent]
     */
    suspend fun onRetry(action: suspend (Int) -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnRetry>()
            .map { it.currentAttempt }
            .collect { action(it) }

    /**
     * Executes the given [action] when an error occurs during a retry attempt.
     * The underlying event is emitted when an exception occurs during a retry attempt.
     * @see [onEvent]
     */
    suspend fun onError(action: suspend (Throwable) -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnError>()
            .map { it.throwable }
            .collect { action(it) }

    /**
     * Executes the given [action] when an error is ignored during a retry attempt.
     * The underlying event is emitted when a retry is not needed to complete the operation
     * (e.g., the exception that occurred is not a retryable exception).
     * @see [onEvent]
     */
    suspend fun onIgnoredError(action: suspend (Throwable) -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnIgnoredError>()
            .map { it.throwable }
            .collect { action(it) }

    /**
     * Executes the given [action] when a retry is successful.
     * The underlying event is emitted when the operation is completed successfully after a retry.
     * @see [onEvent]
     */
    suspend fun onSuccess(action: suspend () -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnSuccess>()
            .collect { action() }

    /**
     * Executes the given [action] when the retry execution is cancelled.
     * The underlying event is emitted when the coroutine is cancelled before the retry execution is completed.
     * @see [onEvent]
     */
    suspend fun onCancellation(action: suspend () -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnCancellation>()
            .collect { action() }

    /**
     * Executes the given [action] when a retry event occurs.
     * This function can be used to listen to all retry events.
     * @see [onRetry]
     * @see [onError]
     * @see [onIgnoredError]
     * @see [onSuccess]
     * @see [onCancellation]
     */
    suspend fun onEvent(action: suspend (RetryEvent) -> Unit) =
        events.collect { action(it) }
}
