package kresil.retry.context

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kresil.retry.RetryEvent
import kresil.retry.config.RetryConfig
import kresil.retry.exceptions.MaxRetriesExceededException
import kotlin.time.Duration

/**
 * Represents the asynchronous context implementation of a retry mechanism.
 * Besides defining context behaviour, this implementation is also responsible for:
 * - state management;
 * - event emission.
 *
 * For each retryable asynchronous operation, a new instance of this class must be created.
 * @param config The configuration for the retry mechanism.
 * @param eventFlow The shared flow to emit retry events to.
 * @see [kresil.retry.Retry]
 */
internal class RetryAsyncContextImpl(
    private val config: RetryConfig,
    private val eventFlow: MutableSharedFlow<RetryEvent>,
) : RetryAsyncContext {

    private companion object {
        const val INITIAL_NON_RETRY_ATTEMPT = 0
    }

    // state
    private var currentRetryAttempt = INITIAL_NON_RETRY_ATTEMPT
    private var lastThrowable: Throwable? = null

    override suspend fun onResult(result: Any?): Boolean {
        if (shouldRetryOnResult(result)) {
            continueOrPropagate(currentRetryAttempt)
            return true
        }
        return false
    }

    override suspend fun onRetry() {
        eventFlow.emit(RetryEvent.RetryOnRetry(++currentRetryAttempt))
        val duration = config.delayStrategy(currentRetryAttempt, lastThrowable)
        if (duration == Duration.ZERO) return
        // TODO: consider giving the user control of the delay function (default should be kotlinx.coroutines.delay)
        // TODO: but be aware that user should be warned about cancellation awareness if delay is implemented in a custom way
        delay(duration)
    }

    override suspend fun onError(throwable: Throwable) {
        lastThrowable = throwable
        if (throwable is MaxRetriesExceededException) {
            throw throwable
        }
        continueOrPropagate(currentRetryAttempt, throwable)
        if (!shouldRetry(throwable)) {
            eventFlow.emit(RetryEvent.RetryOnIgnoredError(throwable))
            throw throwable
        }
    }

    override suspend fun onSuccess() {
        eventFlow.emit(RetryEvent.RetryOnSuccess)
    }

    override suspend fun onCancellation(scope: CoroutineScope, deferred: Deferred<Unit>) {
        deferred.join()
        deferred.invokeOnCompletion { cause ->
            // means that the coroutine was cancelled normally
            if (cause != null && cause is kotlin.coroutines.cancellation.CancellationException) {
                scope.launch {
                    eventFlow.emit(RetryEvent.RetryOnCancellation)
                }
            }
        }
    }

    /**
     * Determines whether the retry mechanism should continue or propagate the error.
     * If the current retry attempt is greater than or equal to the permitted retry attempts, the error is propagated.
     * Otherwise, the retry mechanism continues.
     * If no throwable is provided, it is assumed
     * that the maximum number of attempts was reached
     * when a retry on result was attempted and a [MaxRetriesExceededException] is thrown instead.
     * @param currentRetryAttempt The current retry attempt.
     * @param throwable The throwable to propagate.
     */
    private suspend fun continueOrPropagate(currentRetryAttempt: Int, throwable: Throwable? = null) {
        if (currentRetryAttempt >= config.permittedRetryAttempts) {
            val errorOrExceptionToThrow = throwable ?: MaxRetriesExceededException()
            eventFlow.emit(RetryEvent.RetryOnError(errorOrExceptionToThrow))
            throw errorOrExceptionToThrow
        }
    }

    // utility functions
    private inline fun shouldRetryOnResult(result: Any?): Boolean = config.retryOnResult(result)
    private inline fun shouldRetry(throwable: Throwable): Boolean = config.retryIf(throwable)

}
