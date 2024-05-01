package kresil.retry.config

import kresil.retry.builders.retryConfig
import kresil.retry.Retry

/**
 * Represents a configuration for retrying an operation.
 * A configuration is a collection of policies that determine the behaviour of a retry mechanism.
 * To create an instance, use the [retryConfig] builder.
 * @param maxAttempts the maximum number of attempts **(including the initial call as the first attempt)**.
 * @param retryPredicate the predicate to determine if the operation should be retried based on the caught throwable.
 * @param retryOnResultPredicate the predicate to determine if the operation should be retried based on its result.
 * @param delayStrategy the strategy to determine the delay duration between retries.
 * @see [Retry]
 */
data class RetryConfig internal constructor(
    val maxAttempts: Int,
    val retryPredicate: RetryPredicate,
    val retryOnResultPredicate: RetryOnResultPredicate,
    val delayStrategy: SuspendRetryDelayStrategy,
) {

    /**
     * The number of permitted retry attempts.
     */
    val permittedRetryAttempts: Int = maxAttempts - 1
}
