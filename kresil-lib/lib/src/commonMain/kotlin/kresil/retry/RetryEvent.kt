package kresil.retry

sealed class RetryEvent {
    data class RetryOnRetry(val attempt: Int) : RetryEvent()
    data class RetryOnError(val throwable: Throwable) : RetryEvent()
    data class RetryOnIgnoredError(val throwable: Throwable) : RetryEvent()
    data object RetryOnSuccess : RetryEvent()
}
