package kresil.retry

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kresil.retry.builders.defaultRetryConfig
import kresil.retry.config.RetryConfig
import kresil.retry.context.RetryContextImpl

// TODO: add comments
class Retry(
    val config: RetryConfig = defaultRetryConfig(),
) {
    // events
    private val eventFlow = MutableSharedFlow<RetryEvent>()
    private val events: Flow<RetryEvent> = eventFlow.asSharedFlow()

    suspend fun <T> executeSuspendFunction(block: suspend () -> T) {
        coroutineScope {
            val context = RetryContextImpl(config, eventFlow)
            val deferred = async {
                while (true) {
                    try {
                        val result: Any? = block()
                        val shouldRetry = context.onResult(result)
                        if (shouldRetry) {
                            context.onRetry()
                            continue
                        }
                        context.onSuccess()
                        break
                    } catch (throwable: Throwable) {
                        context.onError(throwable)
                        context.onRetry()
                    }
                }
            }
            withContext(NonCancellable) {
                context.onCancellation(this, deferred)
            }
        }
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
