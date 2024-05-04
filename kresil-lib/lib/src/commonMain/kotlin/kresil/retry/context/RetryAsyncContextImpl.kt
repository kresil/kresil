package kresil.retry.context

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kresil.retry.event.RetryEvent
import kresil.retry.config.RetryConfig
import kresil.retry.Retry
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
 * @see [Retry]
 */
internal class RetryAsyncContextImpl(
    private val config: RetryConfig,
    private val eventFlow: MutableSharedFlow<RetryEvent>
) : RetryAsyncContext {

    companion object {
        const val INITIAL_NON_RETRY_ATTEMPT = 0
    }

    // state
    private var currentRetryAttempt = INITIAL_NON_RETRY_ATTEMPT
    private var lastThrowable: Throwable? = null
    private val isRetryAttempt: Boolean
        get() = currentRetryAttempt > INITIAL_NON_RETRY_ATTEMPT

    override suspend fun onResult(result: Any?): Boolean {
        if (shouldRetryOnResult(result)) {
            continueOrPropagate(currentRetryAttempt)
            return true
        }
        return false
    }

    override suspend fun onRetry() {
        eventFlow.emit(RetryEvent.RetryOnRetry(++currentRetryAttempt))
        val duration: Duration? = config.delayStrategy(currentRetryAttempt, lastThrowable)
        when {
            // skip default delay provider if duration is null (defined externally) or zero (no delay)
            duration == null || duration <= Duration.ZERO -> return
            else -> delay(duration)
        }
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
        if (isRetryAttempt) eventFlow.emit(RetryEvent.RetryOnSuccess)
    }

    override suspend fun beforeOperationCall() {
        if (isRetryAttempt) config.beforeOperationCallback(currentRetryAttempt)
    }

    /**
     * Determines whether the retry mechanism should continue or propagate the error.
     * If the current retry attempt is greater than or equal to the permitted retry attempts, the error is propagated.
     * Otherwise, the retry mechanism continues.
     * Also, if no throwable is provided, it is assumed
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
    private fun shouldRetryOnResult(result: Any?): Boolean = config.retryOnResultPredicate(result)
    private fun shouldRetry(throwable: Throwable): Boolean = config.retryPredicateList.any { it(throwable) }

}
