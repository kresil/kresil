package kresil.retry

/**
 * Represents all possible [Retry] events that can be triggered in a [Retry] mechanism.
 */
sealed class RetryEvent {

    /**
     * Represents a retry event that is triggered when a **retry is attempted**.
     * @param currentAttempt The current attempt number. Starts from **1**.
     */
    data class RetryOnRetry(val currentAttempt: Int) : RetryEvent()

    /**
     * Represents a retry event that is triggered when an **error occurs**.
     * @param throwable The error that occurred.
     */
    data class RetryOnError(val throwable: Throwable) : RetryEvent()

    /**
     * Represents a retry event that is triggered when an **error is ignored**.
     * @param throwable The error that was ignored.
     */
    data class RetryOnIgnoredError(val throwable: Throwable) : RetryEvent()

    /**
     * Represents a retry event that is triggered when a **retry succeeds**.
     */
    data object RetryOnSuccess : RetryEvent()
}
