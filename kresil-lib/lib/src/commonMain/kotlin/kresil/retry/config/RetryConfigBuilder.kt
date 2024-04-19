package kresil.retry.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RetryConfigBuilder {

    companion object {
        private const val DEFAULT_MAX_ATTEMPTS = 3
        private val DEFAULT_DELAY = 3.seconds
        private val DEFAULT_RETRY_PREDICATE: (Throwable) -> Boolean = { true }
    }

    // TODO: add checks for illegal values
    private var maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    private var delay: Duration = DEFAULT_DELAY
    private var retryIf: (Throwable) -> Boolean = DEFAULT_RETRY_PREDICATE

    fun maxAttempts(value: Int) {
        maxAttempts = value
    }

    fun retryIf(predicate: (Throwable) -> Boolean) {
        retryIf = predicate
    }

    fun delay(duration: Duration) {
        delay = duration
    }

    fun build() = RetryConfig(
        maxAttempts,
        retryIf,
        delay
    )
}
