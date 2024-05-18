package kresil.retry.context

import kresil.retry.Retry

/**
 * Specifies the behaviour of the retry mechanism in an asynchronous context.
 * @see [Retry]
 **/
internal interface RetryAsyncContext {

    /**
     * Determines whether a retry should be attempted based on the result of the asynchronous operation.
     * @param result The result of the asynchronous operation.
     * @return `true` if a retry should be attempted, `false` otherwise.
     */
    suspend fun onResult(result: Any?): Boolean

    /**
     * Initiates a retry attempt by applying the delay strategy.
     */
    suspend fun onRetry()

    /**
     * Handles an error that occurred during the asynchronous operation execution.
     * Such handling may include deciding whether to retry the operation or ignore the error and complete the operation.
     * This method might propagate the error if retrying is no longer possible
     * (e.g., the maximum number of attempts was reached), and the caller did not specify a custom error handler.
     * @param throwable The throwable representing the error that occurred.
     * @return `true` if the operation should be retried, `false` otherwise.
     */
    suspend fun onError(throwable: Throwable): Boolean

    /**
     * Handles the successful completion of the asynchronous operation.
     * Even if the operation is completed without retrying once, this method is still called.
     */
    suspend fun onSuccess()


    /**
     * Applies logic before the operation is called in each attempt (including the initial call).
     */
    suspend fun beforeOperationCall()

}

