package kresil.retry

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kresil.core.callbacks.ResultMapper
import kresil.core.events.FlowEventListenerImpl
import kresil.core.oper.BiFunction
import kresil.core.oper.Function
import kresil.core.oper.Supplier
import kresil.retry.config.RetryConfig
import kresil.retry.config.RetryConfigBuilder
import kresil.retry.config.defaultRetryConfig
import kresil.retry.context.RetryAsyncContextImpl
import kresil.retry.event.RetryEvent
import kresil.core.oper.CtxBiFunction
import kresil.core.oper.CtxFunction
import kresil.core.oper.CtxSupplier
import kresil.retry.context.RetryContext

/**
 * A [Retry](https://learn.microsoft.com/en-us/azure/architecture/patterns/retry)
 * resilience mechanism implementation
 * that can be used to retry an operation when it fails and the failure is a transient error.
 * Operations can be decorated and executed on demand.
 * A retry mechanism is initialized with a [RetryConfig] that,
 * through pre-configured policies, define its behaviour.
 *
 * The retry mechanism implements the following state machine:
 * ```
 *
 *                    +------------------+  retried once   +---------+
 * +-----------+ ---> | Returns Normally | --------------> | Success |
 * | Operation |      +------------------+                 +---------+
 * |  Called   |      +-------+      +----------+
 * +-----------+ ---> | Fails | ---> | Consults |
 *       ^            +-------+      | Policies |
 *       |                           +----------+
 *       |                                |
 *   +-------+      can use retry         |
 *   | Retry | <--------------------------|
 *   +-------+                            |   expected
 *                                        |   failure    +-------+
 *                                        |------------> | Error |
 *                                        |              +-------+
 *                     +---------+        |
 *                     | Ignored | <------|
 *                     |  Error  |
 *                     +---------+
 * ```
 * Examples of usage:
 * ```
 * // use predefined retry policies
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
 *       addRetryPredicate { it is NetworkError }
 *       retryOnResultIf { it is "success" }
 *       constantDelay(500.milliseconds)
 *       // customDelay { attempt, context -> ... }
 *       // resultMapper { throwable -> ... }
 *    }
 * )
 *
 * // execute a supplier
 * retry.executeSupplier { ctx ->
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
     * @param resultMapper The mapper to transform the result or the exception.
     * @param block The operation to execute.
     * @see [decorateFunction]
     */
    private suspend fun <A, B, R> executeOperation(
        inputA: A,
        inputB: B,
        resultMapper: (Any?, Throwable?) -> Any? = config.resultMapper, // TODO: missing tests for resultMapper
        block: CtxBiFunction<RetryContext, A, B, R>
    ): R? { // TODO: also decoration principle is lost if function which didn't return null is forced to return null
        val context = RetryAsyncContextImpl(config, events)
        while (true) {
            try {
                val ctx = RetryContext(context.currentRetryAttempt)
                val result = block(ctx, inputA, inputB)
                val shouldRetryOnResult = context.onResult(result)
                if (shouldRetryOnResult) {
                    context.onRetry()
                    continue
                }
                context.onSuccess()
                @Suppress("UNCHECKED_CAST")
                return resultMapper(result, null) as R? // TODO: remove this cast
            } catch (throwable: Throwable) {
                val shouldRetryOnError = context.onError(throwable)
                if (shouldRetryOnError) {
                    context.onRetry()
                } else {
                    @Suppress("UNCHECKED_CAST")
                    return resultMapper(null, throwable) as R?
                }
            }
        }
    }

    /**
     * Executes a [Supplier] with this retry mechanism.
     * @param resultMapper The mapper to transform the result or the exception. By default, the [ResultMapper] from the configuration is used.
     * @param block The operation to execute.
     * @see [decorateSupplier]
     */
    suspend fun <R> executeSupplier(
        resultMapper: ResultMapper = config.resultMapper,
        block: CtxSupplier<RetryContext, R>,
    ): R? { // TODO: decoration principle is lost if function which didn't return null is forced to return null
        return executeOperation(Unit, Unit, resultMapper) { ctx, _, _ -> block(ctx) }
    }

    /**
     * Decorates a [Supplier] with this retry mechanism by providing an additional [RetryContext].
     * @param resultMapper The mapper to transform the result or the exception. By default, the [ResultMapper] from the configuration is used.
     * @param block The operation to decorate and execute later.
     * @see [decorateCtxFunction]
     * @see [decorateCtxBiFunction]
     */
    fun <R> decorateCtxSupplier(
        resultMapper: ResultMapper = config.resultMapper,
        block: CtxSupplier<RetryContext, R>,
    ): Supplier<R?> {
        return { executeSupplier(resultMapper) { block(it) } }
    }

    /**
     * Decorates a [Supplier] with this retry mechanism.
     * @param block The operation to decorate and execute later.
     * @see [decorateFunction]
     * @see [decorateBiFunction]
     */
    fun <R> decorateSupplier(
        block: Supplier<R>,
    ): Supplier<R?> {
        return { executeSupplier { block() } }
    }

    /**
     * Decorates a [Function] with this retry mechanism by providing an additional [RetryContext].
     * @param resultMapper The mapper to transform the result or the exception. By default, the [ResultMapper] from the configuration is used.
     * @param block The operation to decorate and execute later.
     * @see [decorateCtxSupplier]
     * @see [decorateCtxBiFunction]
     */
    fun <A, R> decorateCtxFunction(
        resultMapper: ResultMapper = config.resultMapper,
        block: CtxFunction<RetryContext, A, R>,
    ): Function<A, R?> {
        return { executeOperation(it, Unit, resultMapper) { ctx, a, _ -> block(ctx, a) } }
    }

    /**
     * Decorates a [Function] with this retry mechanism.
     * @param resultMapper The mapper to transform the result or the exception. By default, the [ResultMapper] from the configuration is used.
     * @param block The operation to decorate and execute later.
     * @see [decorateSupplier]
     * @see [decorateBiFunction]
     */
    fun <A, R> decorateFunction(
        resultMapper: ResultMapper = config.resultMapper,
        block: Function<A, R>,
    ): Function<A, R?> {
        return { executeOperation(it, Unit, resultMapper) { _, a, _ -> block(a) } }
    }

    /**
     * Decorates a [BiFunction] with this retry mechanism by providing an additional [RetryContext].
     * @param resultMapper The mapper to transform the result or the exception. By default, the [ResultMapper] from the configuration is used.
     * @param block The operation to decorate and execute later.
     * @see [decorateCtxSupplier]
     * @see [decorateCtxFunction]
     */
    fun <A, B, R> decorateCtxBiFunction(
        resultMapper: ResultMapper = config.resultMapper,
        block: CtxBiFunction<RetryContext, A, B, R>,
    ): BiFunction<A, B, R?> {
        return { a, b -> executeOperation(a, b, resultMapper) { ctx, a2, b2 -> block(ctx, a2, b2) } }
    }

    /**
     * Decorates a [BiFunction] with this retry mechanism.
     * @param resultMapper The mapper to transform the result or the exception. By default, the [ResultMapper] from the configuration is used.
     * @param block The operation to decorate and execute later.
     * @see [decorateSupplier]
     * @see [decorateFunction]
     */
    fun <A, B, R> decorateBiFunction(
        resultMapper: ResultMapper = config.resultMapper,
        block: BiFunction<A, B, R>,
    ): BiFunction<A, B, R?> {
        return { a, b -> executeOperation(a, b, resultMapper) { _, a2, b2 -> block(a2, b2) } }
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
