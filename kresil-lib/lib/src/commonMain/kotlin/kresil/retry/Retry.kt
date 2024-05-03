package kresil.retry

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kresil.core.operations.BiFunction
import kresil.core.events.FlowEventListenerImpl
import kresil.core.operations.Function
import kresil.core.operations.NBiFunction
import kresil.core.operations.NFunction
import kresil.core.operations.NSupplier
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
     * Executes an operation with this retry mechanism.
     * @param inputA The first input argument.
     * @param inputB The second input argument.
     * @param block The operation to execute.
     * @see [decorateFunction]
     */
    private suspend fun <InputA, InputB, Result> executeOperation(
        inputA: InputA,
        inputB: InputB,
        block: NBiFunction<InputA, InputB, Result>
    ): Result? {
        val context = RetryAsyncContextImpl(config, events)
        while (true) {
            try {
                val result = block(inputA, inputB)
                val shouldRetry = context.onResult(result)
                if (shouldRetry) {
                    context.onRetry()
                    continue
                }
                if (context.retryAttempt > RetryAsyncContextImpl.INITIAL_NON_RETRY_ATTEMPT) {
                    context.onSuccess()
                }
                return result
            } catch (throwable: Throwable) {
                context.onError(throwable)
                context.onRetry()
            }
        }
    }

    /**
     * Executes a [Supplier] with this retry mechanism.
     * See [executeNSupplier] for a nullable result version of this operation.
     * @param block The operation to execute.
     * @see [decorateSupplier]
     */
    suspend fun <Result : Any> executeSupplier(
        block: Supplier<Result>
    ): Result {
        return executeNSupplier(block) as Result
    }

    /**
     * Executes a [NSupplier] with this retry mechanism.
     * See [executeSupplier] for a non-nullable version of this operation.
     * @param block The operation to execute.
     * @see [decorateSupplier]
     */
    suspend fun <Result> executeNSupplier(
        block: NSupplier<Result>
    ): Result? {
        return executeOperation(Unit, Unit) { _, _ -> block() }
    }

    /**
     * Decorates a [Supplier] with this retry mechanism.
     * See [decorateNSupplier] for a nullable result version of this operation.
     * @param block The operation to decorate and execute later.
     * @see [decorateBiFunction]
     * @see [decorateFunction]
     */
    fun <Result : Any> decorateSupplier(
        block: Supplier<Result>
    ): Supplier<Result> {
        @Suppress("UNCHECKED_CAST")
        return decorateNSupplier(block) as Supplier<Result>
    }

    /**
     * Decorates a [NSupplier] with this retry mechanism.
     * See [decorateSupplier] for a non-nullable version of this operation.
     * @param block The operation to decorate and execute later.
     * @see [decorateNSupplier]
     * @see [decorateNBiFunction]
     */
    fun <Result> decorateNSupplier(
        block: NSupplier<Result>
    ): NSupplier<Result> {
        return { executeOperation(Unit, Unit) { _, _ -> block() } }
    }

    /**
     * Decorates a [Function] with this retry mechanism.
     * See [decorateNFunction] for a nullable result version of this operation.
     * @param block The operation to decorate and execute later.
     * @see [decorateSupplier]
     * @see [decorateBiFunction]
     */
    fun <Input, Result: Any> decorateFunction(
        block: Function<Input, Result>
    ): Function<Input, Result> {
        @Suppress("UNCHECKED_CAST")
        return decorateNFunction(block) as Function<Input, Result>
    }

    /**
     * Decorates a [NFunction] with this retry mechanism.
     * See [decorateFunction] for a non-nullable version of this operation.
     * @param block The operation to decorate and execute later.
     * @see [decorateNSupplier]
     * @see [decorateNBiFunction]
     */
    fun <Input, Result> decorateNFunction(
        block: NFunction<Input, Result>
    ): NFunction<Input, Result> {
        return { executeOperation(it, Unit) { a, _ -> block(a) } }
    }

    /**
     * Decorates a [BiFunction] with this retry mechanism.
     * See [decorateNBiFunction] for a nullable result version of this operation.
     * @param block The operation to decorate and execute later.
     * @see [decorateSupplier]
     * @see [decorateFunction]
     */
    fun <InputA, InputB, Result: Any> decorateBiFunction(
        block: BiFunction<InputA, InputB, Result>
    ): BiFunction<InputA, InputB, Result> {
        @Suppress("UNCHECKED_CAST")
        return decorateNBiFunction(block) as BiFunction<InputA, InputB, Result>
    }

    /**
     * Decorates a [NBiFunction] with this retry mechanism.
     * See [decorateBiFunction] for a non-nullable version of this operation.
     * @param block The operation to decorate and execute later.
     * @see [decorateNSupplier]
     * @see [decorateNFunction]
     */
    fun <InputA, InputB, Result> decorateNBiFunction(
        block: NBiFunction<InputA, InputB, Result>
    ): NBiFunction<InputA, InputB, Result> {
        return { a, b -> executeOperation(a, b, block) }
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

    /**
     * Executes the given [action] when a retry event occurs.
     * This function can be used to listen to all retry events.
     * @see [onRetry]
     * @see [onError]
     * @see [onIgnoredError]
     * @see [onSuccess]
     * @see [cancelListeners]
     */
    override suspend fun onEvent(action: suspend (RetryEvent) -> Unit) {
        super.onEvent(action)
    }

}
