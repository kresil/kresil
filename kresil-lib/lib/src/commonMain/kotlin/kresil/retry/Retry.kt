package kresil.retry

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kresil.retry.config.RetryConfig
import kresil.retry.exceptions.MaxRetriesExceededException
import kotlin.coroutines.cancellation.CancellationException

// TODO: add comments
class Retry(
    val config: RetryConfig = defaultRetryConfig(),
) {

    private companion object {
        const val INITIAL_ATTEMPT = 0
    }

    // events
    private val eventFlow = MutableSharedFlow<RetryEvent>()
    private val events: Flow<RetryEvent> = eventFlow.asSharedFlow()

    suspend fun <T> executeSuspendFunction(block: suspend () -> T) {
        coroutineScope {
            val deferred = async {
                var currentRetryAttempt = INITIAL_ATTEMPT
                while (true) {
                    try {
                        val result: Any? = block()
                        if (config.shouldRetryOnResult(result)) {
                            canRetry(currentRetryAttempt)
                            currentRetryAttempt = onRetryAttempt(currentRetryAttempt)
                            continue
                        }
                        eventFlow.emit(RetryEvent.RetryOnSuccess)
                        return@async
                    } catch (throwable: Throwable) {
                        if (throwable is MaxRetriesExceededException) {
                            throw throwable
                        }
                        canRetry(currentRetryAttempt, throwable)
                        if (!config.shouldRetry(throwable)) {
                            eventFlow.emit(RetryEvent.RetryOnIgnoredError(throwable))
                            throw throwable
                        }
                        currentRetryAttempt = onRetryAttempt(currentRetryAttempt)
                    }
                }
            }
            withContext(NonCancellable) {
                deferred.join()
                deferred.invokeOnCompletion { cause ->
                    // means that the coroutine was cancelled normally
                    if (cause != null && cause is CancellationException) {
                        launch {
                            eventFlow.emit(RetryEvent.RetryOnCancellation)
                        }
                    }
                }
            }
        }
    }

    private suspend fun canRetry(currentRetryAttempt: Int, throwable: Throwable? = null) {
        if (currentRetryAttempt >= config.permittedRetryAttempts) {
            val _throwable = throwable ?: MaxRetriesExceededException()
            eventFlow.emit(RetryEvent.RetryOnError(_throwable))
            throw _throwable
        }
    }

    private suspend fun onRetryAttempt(previousRetryAttempt: Int): Int {
        val currentRetryAttempt = previousRetryAttempt + 1
        eventFlow.emit(RetryEvent.RetryOnRetry(currentRetryAttempt))
        delay(config.delay.inWholeMilliseconds)
        return currentRetryAttempt
    }

    fun <T> decorateSuspendFunction(block: suspend () -> T): suspend () -> Unit = {
        executeSuspendFunction(block)
    }

    suspend fun onRetry(action: suspend (Int) -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnRetry>()
            .map { it.currentAttempt }
            .collect { action(it) }

    suspend fun onError(action: suspend (Throwable) -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnError>()
            .map { it.throwable }
            .collect { action(it) }

    suspend fun onIgnoredError(action: suspend (Throwable) -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnIgnoredError>()
            .map { it.throwable }
            .collect { action(it) }

    suspend fun onSuccess(action: suspend () -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnSuccess>()
            .collect { action() }

    @Suppress("Unused")
    suspend fun onCancellation(action: suspend () -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnCancellation>()
            .collect { action() }

    suspend fun onEvent(action: suspend (RetryEvent) -> Unit) =
        events.collect { action(it) }
}
