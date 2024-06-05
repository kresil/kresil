package kresil.ktor.client.plugins.retry.exceptions

import kresil.ktor.client.plugins.retry.config.RetryPluginConfigBuilder

/**
 * Exception thrown when the [RetryPluginConfigBuilder.retryOnCallPredicate] predicate returns true,
 * after an [io.ktor.client.call.HttpClientCall] has been issued and the call couldn't be retried due to the retry configuration (e.g., maximum attempts reached).
 */
internal class RetryOnCallException : Exception("The retry-on-call predicate returned true after an HTTP client call was issued, but the call couldn't be retried due to the retry configuration (e.g., maximum attempts reached).")
