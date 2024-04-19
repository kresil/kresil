package kresil.retry.config

import kotlin.time.Duration

data class RetryConfig(
    val maxAttempts: Int,
    val retryIf: (Throwable) -> Boolean,
    val delay: Duration
) {
    inline fun shouldRetry(throwable: Throwable) = retryIf(throwable)
}
