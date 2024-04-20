package kresil.retry

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kresil.retry.config.RetryConfig

// TODO: add comments
class Retry(
    val config: RetryConfig,
) {

    companion object {
        const val INITIAL_ATTEMPT = 0
    }

    init {
        Napier.base(DebugAntilog("Retry"))
    }

    private val eventFlow = MutableSharedFlow<RetryEvent>()

    // TODO: consider making it public
    private val events: Flow<RetryEvent> = eventFlow.asSharedFlow()

    suspend fun <T> executeSuspendFunction(block: suspend () -> T) {
        Napier.i { "Executing suspend function with retry" }
        // TODO: attempts counter is not thread-safe, add atomicfu impl
        var retryAttempt = INITIAL_ATTEMPT
        // TODO: hold last exception with concurrency-safe data structure
        while (true) {
            try {
                // reminder: the first attempt is not a retry attempt
                if (retryAttempt > INITIAL_ATTEMPT) {
                    Napier.i { "Retry attempt: $retryAttempt" }
                }
                block()
                eventFlow.emit(RetryEvent.RetryOnSuccess)
                return
            } catch (e: Throwable) {
                if (retryAttempt >= config.maxAttempts - 1) {
                    Napier.e { "Max attempts reached, propagating..." }
                    eventFlow.emit(RetryEvent.RetryOnError(e))
                    throw e
                }
                if (!config.shouldRetry(e)) {
                    Napier.e { "Expected exception, propagating..." }
                    eventFlow.emit(RetryEvent.RetryOnIgnoredError(e))
                    throw e
                }
                retryAttempt++
                eventFlow.emit(RetryEvent.RetryOnRetry(retryAttempt))
                Napier.i { "Delaying for ${config.delay}" }
                delay(config.delay.inWholeMilliseconds)
            }
        }
    }

    fun <T> decorateSuspendFunction(block: suspend () -> T): suspend () -> Unit = {
        executeSuspendFunction(block)
    }

    suspend fun onRetry(action: suspend (Int) -> Unit) =
        events
            .filterIsInstance<RetryEvent.RetryOnRetry>()
            .map { it.attempt }
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

    suspend fun onEvent(action: suspend (RetryEvent) -> Unit) =
        events.collect { action(it) }
}
