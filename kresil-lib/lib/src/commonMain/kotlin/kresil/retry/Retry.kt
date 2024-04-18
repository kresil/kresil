package kresil.retry

import kotlinx.coroutines.delay
import kresil.retry.config.RetryConfig

class Retry(
    private val config: RetryConfig,
) {
    suspend fun <T> executeSuspendFunction(block: suspend () -> T): T {
        // TODO: counter is not thread-safe, add atomicfu impl
        var attempts = 1
        while (true) {
            try {
                block()
            } catch (e: Throwable) {
                if (!config.shouldRetry(e)) {
                    throw e
                }
                attempts++
                if (attempts >= config.maxAttempts) {
                    throw e
                }
                delay(config.delay.nanoseconds)
            }
        }
    }

    // TODO: how to intercept suspend function?
    fun <T> decorateSuspendFunction(block: suspend () -> T): suspend () -> T {
        return {
            executeSuspendFunction(block)
        }
    }

}
