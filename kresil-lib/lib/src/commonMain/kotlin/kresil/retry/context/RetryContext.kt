package kresil.retry.context
/**
 * Exposes information about the current retry context.
 * @param attempt The current attempt number. Might be 0 if the operation was not retried yet.
 */
data class RetryContext(
    val attempt: Int,
)
