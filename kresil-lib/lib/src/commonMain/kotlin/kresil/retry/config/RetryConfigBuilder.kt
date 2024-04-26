package kresil.retry.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// TODO: revisit visibility concerns
class RetryConfigBuilder {

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        val DEFAULT_DELAY = 500.milliseconds
        val DEFAULT_RETRY_PREDICATE: (Throwable) -> Boolean = { true }
        val DEFAULT_RETRY_ON_RESULT_PREDICATE: (Any?) -> Boolean = { false }
    }

    private var maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    private var delay: Duration = DEFAULT_DELAY
    private var retryIf: (Throwable) -> Boolean = DEFAULT_RETRY_PREDICATE
    private var retryOnResult: (Any?) -> Boolean = DEFAULT_RETRY_ON_RESULT_PREDICATE

    fun maxAttempts(value: Int) {
        maxAttempts = value
    }

    fun retryIf(predicate: (Throwable) -> Boolean) {
        retryIf = predicate
    }

    fun retryOnResult(predicate: (Any?) -> Boolean) {
        retryOnResult = predicate
    }

    fun delay(duration: Duration) {
        delay = duration
    }

    fun build() = RetryConfig(
        maxAttempts,
        retryIf,
        retryOnResult,
        delay
    )
}
