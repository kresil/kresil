package kresil.retry

import kotlinx.coroutines.Job
import kresil.core.callbacks.ResultMapper
import kresil.core.events.FlowEventListenerImpl
import kresil.core.oper.BiFunction
import kresil.core.oper.CtxBiFunction
import kresil.core.oper.CtxFunction
import kresil.core.oper.CtxSupplier
import kresil.core.oper.Function
import kresil.core.oper.Supplier
import kresil.retry.config.RetryConfig
import kresil.retry.config.defaultRetryConfig
import kresil.retry.config.retryConfig
import kresil.retry.context.RetryAsyncContextImpl
import kresil.retry.context.RetryContext
import kresil.retry.event.RetryEvent
import kresil.retry.event.RetryEvent.RetryOnError
import kresil.retry.event.RetryEvent.RetryOnIgnoredError
import kresil.retry.event.RetryEvent.RetryOnRetry
import kresil.retry.event.RetryEvent.RetryOnSuccess

/**
 * A [Retry](https://learn.microsoft.com/en-us/azure/architecture/patterns/retry)
 * is a reactive resilience mechanism
 * that can be used to retry an operation when it fails and the failure is a transient (temporary) fault.
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
 * val retry = Retry()
 *
 * // use custom policies
 * val retry = Retry(
 *    retryConfig {
 *       maxAttempts = 5 // initial call + 4 retries
 *       retryIf { it is NetworkError }
 *       retryOnResult { it is "success" }
 *       constantDelay(500.milliseconds)
 *       // customDelay { attempt, context -> ... }
 *       // exceptionHandler { exception -> ... }
 *    }
 * )
 *
 * // execute a supplier
 * retry.executeSupplier {
 *    // operation
 * }
 *
 * // execute a supplier with a context
 * retry.executeCtxSupplier { ctx ->
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
 * retry.onRetry { event -> println(event) }
 *
 * // listen to all events
 * retry.onEvent { event -> println(event) }
 *
 * // cancel all registered listeners
 * retry.cancelListeners()
 * ```
 * @param config The configuration for the retry mechanism.
 * @see [retryConfig]
 */
class Retry(
    val config: RetryConfig = defaultRetryConfig(),
) : FlowEventListenerImpl<RetryEvent>() {

    /**
     * Provides a default result mapper for decorating functions which returns the result unmodified or applies
     * the exception handler from the configuration.
     */
    @PublishedApi
    internal fun <R, T> defaultResultMapper(): ResultMapper<R, T> = { result: R?, throwable: Throwable? ->
        // throwable must be checked first because a result being nullable is a viable option
        if (throwable != null) {
            config.exceptionHandler(throwable)
            null
        } else {
            @Suppress("UNCHECKED_CAST")
            result as T?
        }
    }

    /**
     * Executes an operation with this retry mechanism.
     * @param inputA The first input argument.
     * @param inputB The second input argument.
     * @param resultMapper The mapper to transform the result or the exception.
     * @param block The operation to execute.
     * @see [decorateFunction]
     */
    @PublishedApi
    internal suspend fun <A, B, R, T> executeOperation(
        inputA: A,
        inputB: B,
        resultMapper: ResultMapper<R, T>,
        block: CtxBiFunction<RetryContext, A, B, R>
    ): T? { // TODO: decoration principle is lost if function which didn't return null is forced to return null
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
                return resultMapper(result, null)
            } catch (throwable: Throwable) {
                val shouldRetryOnError = context.onError(throwable)
                if (shouldRetryOnError) {
                    context.onRetry()
                } else {
                    return resultMapper(null, throwable)
                }
            }
        }
    }

    /**
     * Executes a [Supplier] with this retry mechanism.
     * @param block The operation to execute.
     * @see [decorateSupplier]
     */
    suspend inline fun <R> executeSupplier(
        crossinline block: Supplier<R>,
    ): R? {
        return executeOperation(Unit, Unit, defaultResultMapper<R, R>()) { _, _, _ -> block() }
    }

    /**
     * Executes a [Supplier] with this retry mechanism.
     * @param resultMapper The mapper to transform the result or the exception.
     * By default, no result mapping is performed, and exception handling defaults to the handler
     * configured in the configuration.
     * @param block The operation to execute.
     * @see [decorateSupplier]
     */
    suspend inline fun <R, T> executeCtxSupplier(
        noinline resultMapper: ResultMapper<R, T> = defaultResultMapper(),
        crossinline block: CtxSupplier<RetryContext, R>,
    ): T? {
        return executeOperation(Unit, Unit, resultMapper) { ctx, _, _ -> block(ctx) }
    }

    /**
     * Decorates a [Supplier] with this retry mechanism.
     * @param block The operation to decorate and execute later.
     * @see [decorateFunction]
     * @see [decorateBiFunction]
     */
    inline fun <R> decorateSupplier(
        crossinline block: Supplier<R>,
    ): Supplier<R?> {
        return { executeSupplier { block() } }
    }

    /**
     * Decorates a [Supplier] with this retry mechanism by providing an additional [RetryContext].
     * @param resultMapper The mapper to transform the result or the exception.
     * By default, no result mapping is performed, and exception handling defaults to the handler
     * configured in the configuration.
     * @param block The operation to decorate and execute later.
     * @see [decorateCtxFunction]
     * @see [decorateCtxBiFunction]
     */
    inline fun <R, T> decorateCtxSupplier(
        noinline resultMapper: ResultMapper<R, T> = defaultResultMapper(),
        crossinline block: CtxSupplier<RetryContext, R>
    ): Supplier<T?> {
        return { executeCtxSupplier(resultMapper) { block(it) } }
    }

    /**
     * Decorates a [Function] with this retry mechanism.
     * @param block The operation to decorate and execute later.
     * @see [decorateSupplier]
     * @see [decorateBiFunction]
     */
    inline fun <A, R> decorateFunction(
        crossinline block: Function<A, R>,
    ): Function<A, R?> {
        return { executeOperation(it, Unit, defaultResultMapper<R, R>()) { _, a, _ -> block(a) } }
    }

    /**
     * Decorates a [Function] with this retry mechanism by providing an additional [RetryContext].
     * @param resultMapper The mapper to transform the result or the exception.
     * By default, no result mapping is performed, and exception handling defaults to the handler
     * configured in the configuration.
     * @param block The operation to decorate and execute later.
     * @see [decorateCtxSupplier]
     * @see [decorateCtxBiFunction]
     */
    inline fun <A, R, T> decorateCtxFunction(
        noinline resultMapper: ResultMapper<R, T> = defaultResultMapper(),
        crossinline block: CtxFunction<RetryContext, A, R>,
    ): Function<A, T?> {
        return { executeOperation(it, Unit, resultMapper) { ctx, a, _ -> block(ctx, a) } }
    }

    /**
     * Decorates a [BiFunction] with this retry mechanism.
     * @param block The operation to decorate and execute later.
     * @see [decorateSupplier]
     * @see [decorateFunction]
     */
    inline fun <A, B, R> decorateBiFunction(
        crossinline block: BiFunction<A, B, R>,
    ): BiFunction<A, B, R?> {
        return { a, b -> executeOperation(a, b, defaultResultMapper<R, R>()) { _, a2, b2 -> block(a2, b2) } }
    }

    /**
     * Decorates a [BiFunction] with this retry mechanism by providing an additional [RetryContext].
     * @param resultMapper The mapper to transform the result or the exception.
     * By default, no result mapping is performed, and exception handling defaults to the handler
     * configured in the configuration.
     * @param block The operation to decorate and execute later.
     * @see [decorateCtxSupplier]
     * @see [decorateCtxFunction]
     */
    inline fun <A, B, R, T> decorateCtxBiFunction(
        noinline resultMapper: ResultMapper<R, T> = defaultResultMapper(),
        crossinline block: CtxBiFunction<RetryContext, A, B, R>,
    ): BiFunction<A, B, T?> {
        return { a, b -> executeOperation(a, b, resultMapper) { ctx, a2, b2 -> block(ctx, a2, b2) } }
    }

    /**
     * Executes the given [action] when a retry is attempted.
     * The underlying event is emitted when a retry is attempted, **before entering the delay phase**.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onRetry(action: suspend (RetryOnRetry) -> Unit): Job =
        onSpecificEvent(action)

    /**
     * Executes the given [action] when an error occurs during a retry attempt.
     * The underlying event is emitted when an exception occurs during a retry attempt.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onError(action: suspend (RetryOnError) -> Unit): Job =
        onSpecificEvent(action)

    /**
     * Executes the given [action] when an error is ignored during a retry attempt.
     * The underlying event is emitted when a retry is not needed to complete the operation
     * (e.g., the exception that occurred is not a retryable exception).
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onIgnoredError(action: suspend (RetryOnIgnoredError) -> Unit): Job =
        onSpecificEvent(action)

    /**
     * Executes the given [action] when a retry is successful.
     * The underlying event is emitted when the operation is completed successfully after a retry.
     * @see [onEvent]
     */
    suspend fun onSuccess(action: suspend (RetryOnSuccess) -> Unit): Job =
        onSpecificEvent(action)

    /**
     * Executes the given [action] when a retry event occurs.
     * This function can be used to listen to all retry events.
     * @see [onRetry]
     * @see [onError]
     * @see [onIgnoredError]
     * @see [onSuccess]
     * @see [cancelListeners]
     */
    override suspend fun onEvent(action: suspend (RetryEvent) -> Unit): Job =
        super.onEvent(action)

}
