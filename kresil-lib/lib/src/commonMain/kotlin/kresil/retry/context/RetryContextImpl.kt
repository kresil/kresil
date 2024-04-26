package kresil.retry.context

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kresil.retry.RetryEvent
import kresil.retry.config.RetryConfig
import kresil.retry.exceptions.MaxRetriesExceededException

internal class RetryContextImpl(
    private val config: RetryConfig,
    private val eventFlow: MutableSharedFlow<RetryEvent>,
) : RetryContext {

    private companion object {
        const val INITIAL_ATTEMPT = 0
    }

    private var currentRetryAttempt = INITIAL_ATTEMPT

    override suspend fun onResult(result: Any?): Boolean {
        if (config.shouldRetryOnResult(result)) {
            canRetry(currentRetryAttempt)
            return true
        }
        return false
    }

    override suspend fun onRetry() {
        eventFlow.emit(RetryEvent.RetryOnRetry(++currentRetryAttempt))
        delay(config.delay.inWholeMilliseconds)
    }

    override suspend fun onError(throwable: Throwable) {
        if (throwable is MaxRetriesExceededException) {
            throw throwable
        }
        canRetry(currentRetryAttempt, throwable)
        if (!config.shouldRetry(throwable)) {
            eventFlow.emit(RetryEvent.RetryOnIgnoredError(throwable))
            throw throwable
        }
    }

    override suspend fun onSuccess() {
        eventFlow.emit(RetryEvent.RetryOnSuccess)
    }

    override suspend fun onCancellation(deferred: Deferred<Unit>) {
        withContext(NonCancellable) {
            deferred.join()
            deferred.invokeOnCompletion { cause ->
                // means that the coroutine was cancelled normally
                if (cause != null && cause is kotlin.coroutines.cancellation.CancellationException) {
                    launch {
                        eventFlow.emit(RetryEvent.RetryOnCancellation)
                    }
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

}
