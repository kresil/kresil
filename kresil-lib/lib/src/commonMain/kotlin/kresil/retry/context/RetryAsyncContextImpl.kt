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
 * - **state management**;
 * - **event emission**.
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
    var currentRetryAttempt = INITIAL_NON_RETRY_ATTEMPT
        private set
    var lastThrowable: Throwable? = null
        private set
    private val isRetryAttempt: Boolean
        get() = currentRetryAttempt > INITIAL_NON_RETRY_ATTEMPT
    private val isWithinPermittedRetryAttempts: Boolean
        get() = currentRetryAttempt < config.permittedRetryAttempts

    override suspend fun onResult(result: Any?): Boolean {
        if (shouldRetryOnResult(result)) {
            if (!isWithinPermittedRetryAttempts) {
                val exception = MaxRetriesExceededException()
                eventFlow.emit(RetryEvent.RetryOnError(exception))
                config.exceptionHandler(exception) // could throw exception
                return false
            }
            return true
        } else {
            return false
        }
    }

    override suspend fun onRetry() {
        eventFlow.emit(RetryEvent.RetryOnRetry(++currentRetryAttempt))
        val duration: Duration = config.delayStrategy(currentRetryAttempt, lastThrowable)
        when {
            // skip default delay provider if duration zero, because the delay is:
            // 1. defined externally;
            // 2. not needed (no delay strategy option)
            duration == Duration.ZERO -> return
            else -> delay(duration)
        }
    }

    override suspend fun onError(throwable: Throwable): Boolean {
        lastThrowable = throwable
        // special case (only for default error handler)
        if (throwable is MaxRetriesExceededException) {
            // propagate exception to the caller
            config.exceptionHandler(throwable)
        }
        // can retry be done for this error?
        if (!shouldRetry(throwable)) {
            eventFlow.emit(RetryEvent.RetryOnIgnoredError(throwable))
            config.exceptionHandler(throwable)
            return false
        }
        // and if retry can be done, do configured policies allow it?
        if (!isWithinPermittedRetryAttempts) {
            eventFlow.emit(RetryEvent.RetryOnError(throwable))
            config.exceptionHandler(throwable)
            return false
        }
        return true
    }

    override suspend fun onSuccess() {
        if (isRetryAttempt && isWithinPermittedRetryAttempts) eventFlow.emit(RetryEvent.RetryOnSuccess)
    }

    // utility functions
    private fun shouldRetryOnResult(result: Any?): Boolean = config.retryOnResultPredicate(result)
    private fun shouldRetry(throwable: Throwable): Boolean = config.retryPredicate(throwable)

}
