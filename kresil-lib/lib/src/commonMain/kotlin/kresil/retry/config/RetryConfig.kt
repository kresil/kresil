package kresil.retry.config

/**
 * Represents a configuration for retrying an operation.
 * A configuration is a collection of policies that determine the behavior of the retry mechanism.
 * To create an instance, use the [kresil.retry.builders.retryConfig] function.
 * @param maxAttempts the maximum number of attempts **(including the initial call as the first attempt)**.
 * @param retryIf the predicate to determine if the operation should be retried based on the caught throwable.
 * @param retryOnResult the predicate to determine if the operation should be retried based on the result of the operation.
 * @param delayStrategy the strategy to determine the delay between retries.
 * @see kresil.retry.Retry
 */
data class RetryConfig internal constructor(
    val maxAttempts: Int,
    val retryIf: RetryPredicate,
    val retryOnResult: RetryOnResultPredicate,
    val delayStrategy: RetryDelayStrategy,
) {

    /**
     * The number of permitted retry attempts.
     */
    val permittedRetryAttempts: Int = maxAttempts - 1
}
