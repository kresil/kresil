package kresil.retry.config

import kresil.core.callbacks.ResultMapper
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
 * @param resultMapper the callback to execute when an error occurs.
 * @see [Retry]
 */
data class RetryConfig(
    val maxAttempts: Int,
    val retryPredicate: OnExceptionPredicate,
    val retryOnResultPredicate: OnResultPredicate,
    val delayStrategy: RetryDelayStrategy,
    val resultMapper: ResultMapper
) {

    /**
     * The number of permitted retry attempts.
     */
    val permittedRetryAttempts: Int = maxAttempts - 1
}
