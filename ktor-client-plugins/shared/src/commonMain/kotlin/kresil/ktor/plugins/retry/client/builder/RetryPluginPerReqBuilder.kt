package kresil.ktor.plugins.retry.client.builder

/**
 * Builder for configuring the [KresilRetryPlugin] on a per-request basis.
 */
class RetryPluginPerReqBuilder : RetryPluginBuilder() {
    fun disableRetry() {
        retryPredicateList.clear()
        shouldRetryOnCallList.clear()
        maxAttempts = 1
    }
}
