package kresil.retry.config

import kresil.duration.Duration

data class RetryConfig(
    val maxAttempts: Int,
    val retryIf: (Throwable) -> Boolean,
    val delay: Duration
) {
    fun shouldRetry(throwable: Throwable): Boolean {
        return retryIf(throwable)
    }
}
