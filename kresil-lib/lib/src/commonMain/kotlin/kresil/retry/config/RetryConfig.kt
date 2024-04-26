package kresil.retry.config

import kotlin.time.Duration

data class RetryConfig(
    val maxAttempts: Int,
    val retryIf: (Throwable) -> Boolean,
    val retryOnResult: (Any?) -> Boolean,
    val delay: Duration
) {
    fun shouldRetryOnResult(result: Any?): Boolean = retryOnResult(result)
    fun shouldRetry(throwable: Throwable): Boolean = retryIf(throwable)
    val permittedRetryAttempts: Int = maxAttempts - 1
}
