package kresil.retry

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kresil.core.operations.BiFunction
import kresil.core.events.FlowEventListenerImpl
import kresil.core.operations.Function
import kresil.core.operations.Supplier
import kresil.retry.builders.defaultRetryConfig
import kresil.retry.config.RetryConfig
import kresil.retry.config.RetryConfigBuilder
import kresil.retry.context.RetryAsyncContextImpl
import kresil.retry.event.RetryEvent

/**
 * Represents a retry mechanism that can be used to retry an operation.
 * Operations can be executed directly or decorated with this mechanism.
 *
 * Examples of usage:
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
 * // execute a supplier
 * retry.executeSupplier {
 *    // operation
 * }
 *
 * // decorate a supplier
 * val decoratedSupplier = retry.decorateSupplier {
 *    // operation
 * }
 * // and call it later
 * val result = decoratedSupplier()
 *
 * // listen to specific events
 * retry.onRetry { attempt -> println("Attempt: $attempt") }
 * retry.onError { throwable -> println("Error: $throwable") }
 * retry.onIgnoredError { throwable -> println("Ignored error: $throwable") }
 * retry.onSuccess { println("Success") }
 *
 * // listen to all events
 * retry.onEvent { event -> println(event) }
 *
 * // cancel all listeners
 * retry.cancelListeners()
 * ```
 * @param config The configuration for the retry mechanism.
 * @see [RetryConfigBuilder]
 */
class Retry(
    val config: RetryConfig = defaultRetryConfig(),
) : FlowEventListenerImpl<RetryEvent>() {

    /**
     * Executes a [BiFunction] with this retry mechanism.
     * @param block The operation to execute.
     * @see [decorateFunction]
     */
    private suspend fun <A, B, R> executeBiFunction(a: A, b: B, block: BiFunction<A, B, R>): R? {
        val context = RetryAsyncContextImpl(config, events)
        while (true) {
            try {
                val result = block(a, b)
                val shouldRetry = context.onResult(result)
                if (shouldRetry) {
                    context.onRetry()
                    continue
                }
                // TODO: should emit retry on success event if no retry was needed to complete?
                context.onSuccess()
                return result
            } catch (throwable: Throwable) {
                context.onError(throwable)
                context.onRetry()
            }
        }
    }

    /**
     * Executes a [Supplier] with this retry mechanism.
     * @param block The operation to execute.
     * @see [decorateSupplier]
     */
    suspend fun <R> executeSupplier(block: Supplier<R>): R? {
        return executeBiFunction(Unit, Unit) { _, _ -> block() }
    }

    /**
     * Decorates a [Supplier] with this retry mechanism.
     * @param block The operation to decorate and execute later.
     * @see [decorateSupplier]
     * @see [decorateFunction]
     */
    fun <A, B, R> decorateBiFunction(block: BiFunction<A, B, R>): BiFunction<A, B, R> {
        return { a, b -> executeBiFunction(a, b, block) }
    }

    /**
     * Decorates a [Function] with this retry mechanism.
     * @param block The operation to decorate and execute later.
     * @see [decorateSupplier]
     * @see [decorateBiFunction]
     */
    fun <A, B> decorateFunction(block: Function<A, B>): Function<A, B> {
        return { executeBiFunction(it, Unit) { a, _ -> block(a) } }
    }

    /**
     * Decorates a [Supplier] with this retry mechanism.
     * @param block The operation to decorate and execute later.
     * @see [decorateBiFunction]
     * @see [decorateFunction]
     */
    fun <B> decorateSupplier(block: Supplier<B>): Supplier<B> {
        return { executeBiFunction(Unit, Unit) { _, _ -> block() } }
    }

    /**
     * Executes the given [action] when a retry is attempted.
     * The underlying event is emitted when a retry is attempted, **before entering the delay phase**.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onRetry(action: suspend (Int) -> Unit) =
        scope.launch {
            events
                .filterIsInstance<RetryEvent.RetryOnRetry>()
                .map { it.attempt }
                .collect { action(it) }
        }

    /**
     * Executes the given [action] when an error occurs during a retry attempt.
     * The underlying event is emitted when an exception occurs during a retry attempt.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onError(action: suspend (Throwable) -> Unit) =
        scope.launch {
            events
                .filterIsInstance<RetryEvent.RetryOnError>()
                .map { it.throwable }
                .collect { action(it) }
        }

    /**
     * Executes the given [action] when an error is ignored during a retry attempt.
     * The underlying event is emitted when a retry is not needed to complete the operation
     * (e.g., the exception that occurred is not a retryable exception).
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onIgnoredError(action: suspend (Throwable) -> Unit) =
        scope.launch {
            events
                .filterIsInstance<RetryEvent.RetryOnIgnoredError>()
                .map { it.throwable }
                .collect { action(it) }
        }

    /**
     * Executes the given [action] when a retry is successful.
     * The underlying event is emitted when the operation is completed successfully after a retry.
     * @see [onEvent]
     */
    suspend fun onSuccess(action: suspend () -> Unit) =
        scope.launch {
            events
                .filterIsInstance<RetryEvent.RetryOnSuccess>()
                .collect { action() }
        }

}
