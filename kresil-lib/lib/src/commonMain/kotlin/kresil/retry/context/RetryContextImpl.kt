package kresil.retry.context

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kresil.retry.RetryEvent
import kresil.retry.config.RetryConfig
import kresil.retry.exceptions.MaxRetriesExceededException

internal class RetryContextImpl(
    private val config: RetryConfig,
    private val eventFlow: MutableSharedFlow<RetryEvent>,
) : RetryContext {

    private companion object {
        const val INITIAL_NON_RETRY_ATTEMPT = 0
    }

    private var currentRetryAttempt = INITIAL_NON_RETRY_ATTEMPT
    private var lastThrowable: Throwable? = null

    override suspend fun onResult(result: Any?): Boolean {
        if (shouldRetryOnResult(result)) {
            canRetry(currentRetryAttempt)
            return true
        }
        return false
    }

    override suspend fun onRetry() {
        eventFlow.emit(RetryEvent.RetryOnRetry(++currentRetryAttempt))
        val duration = config.delay(currentRetryAttempt, lastThrowable)
        delay(duration.inWholeMilliseconds)
    }

    override suspend fun onError(throwable: Throwable) {
        lastThrowable = throwable
        if (throwable is MaxRetriesExceededException) {
            throw throwable
        }
        canRetry(currentRetryAttempt, throwable)
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

    private suspend fun canRetry(currentRetryAttempt: Int, throwable: Throwable? = null) {
        if (currentRetryAttempt >= config.permittedRetryAttempts) {
            val errorOrExceptionToThrow = throwable ?: MaxRetriesExceededException()
            eventFlow.emit(RetryEvent.RetryOnError(errorOrExceptionToThrow))
            throw errorOrExceptionToThrow
        }
    }

    private fun shouldRetryOnResult(result: Any?): Boolean = config.retryOnResult(result)
    private fun shouldRetry(throwable: Throwable): Boolean = config.retryIf(throwable)

}
