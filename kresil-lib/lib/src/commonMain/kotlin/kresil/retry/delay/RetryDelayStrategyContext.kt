package kresil.retry.delay

/**
 * Represents additional context to the [RetryDelayStrategy].
 * @param lastThrowable The last throwable caught that was recorded as a failure, if any.
 * @param lastResult The result of the last successful attempt that was recorded as a failure, if any.
 */
data class RetryDelayStrategyContext(
    val lastThrowable: Throwable? = null,
    val lastResult: Any? = null, // TODO: necessary?
)
