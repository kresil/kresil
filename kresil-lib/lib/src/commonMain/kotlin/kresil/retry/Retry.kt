package kresil.retry

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kresil.retry.config.RetryConfig

class Retry(
    private val config: RetryConfig,
) {

    init {
        Napier.base(DebugAntilog("Retry"))
    }

    suspend fun <T> executeSuspendFunction(block: suspend () -> T): T {
        Napier.i { "Executing suspend function with retry"}
        // TODO: counter is not thread-safe, add atomicfu impl
        var attempts = 1
        // TODO: hold last exception with concurrency-safe data structure
        // and throw if attempts >= maxAttempts
        while (true) {
            try {
                Napier.i { "Attempt: $attempts" }
                block()
            } catch (e: Throwable) {
                if (!config.shouldRetry(e)) {
                    Napier.e { "Expected exception, propagating..." }
                    throw e
                }
                attempts++
                if (attempts > config.maxAttempts) {
                    Napier.e { "Max attempts reached, propagating..." }
                    throw e
                }
                Napier.i { "Delaying for ${config.delay}" }
                delay(config.delay.inWholeNanoseconds)
            }
        }
    }

    fun <T> decorateSuspendFunction(block: suspend () -> T): suspend () -> T = {
        executeSuspendFunction(block)
    }
}
