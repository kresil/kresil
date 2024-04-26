package kresil.retry.context

import kotlinx.coroutines.Deferred

internal interface RetryContext {
    suspend fun onResult(result: Any?): Boolean
    suspend fun onRetry()
    suspend fun onError(throwable: Throwable)
    suspend fun onSuccess()
    suspend fun onCancellation(deferred: Deferred<Unit>)
}
