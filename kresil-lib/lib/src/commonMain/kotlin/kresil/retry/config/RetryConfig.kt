package kresil.retry.config

import kresil.core.callbacks.ExceptionHandler
import kresil.core.callbacks.OnExceptionPredicate
import kresil.core.callbacks.OnResultPredicate
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
 * @param exceptionHandler the handler for exceptions that occur during a retry operation.
 * @see [Retry]
 */
data class RetryConfig(
    val maxAttempts: Int,
    val retryPredicate: OnExceptionPredicate,
    val retryOnResultPredicate: OnResultPredicate,
    val delayStrategy: RetryDelayStrategy,
    val exceptionHandler: ExceptionHandler
) {

    /**
     * The number of permitted retry attempts.
     */
    val permittedRetryAttempts: Int = maxAttempts - 1
}
