package kresil.retry.config

import kresil.retry.Retry
import kresil.retry.delay.RetryDelayStrategy

/**
 * Represents a configuration for retrying an operation.
 * A configuration is a collection of policies that determine the behaviour of a retry mechanism.
 * To create an instance, use the [retryConfig] builder.
 * @param maxAttempts the maximum number of attempts **(including the initial call as the first attempt)**.
 * @param retryPredicate the predicate to determine if the operation should be retried based on the caught throwable.
 * @param retryOnResultPredicate the predicate to determine if the operation should be retried based on its result.
 * @param delayStrategy the strategy to determine the delay duration between retries.
 * @param beforeOperationCallback the callback to execute before the operation is called.
 * @param exceptionHandler the callback to execute when an error occurs.
 * @see [Retry]
 */
data class RetryConfig(
    val maxAttempts: Int,
    val retryPredicate: RetryPredicate,
    val retryOnResultPredicate: RetryOnResultPredicate,
    val delayStrategy: RetryDelayStrategy,
    val beforeOperationCallback: BeforeOperationCallback,
    val exceptionHandler: ExceptionHandler
) {

    /**
     * The number of permitted retry attempts.
     */
    val permittedRetryAttempts: Int = maxAttempts - 1
}
