package kresil.ktor.plugins.retry.client.exceptions

import kresil.ktor.plugins.retry.client.builder.RetryPluginConfigBuilder

/**
 * Exception thrown when the [RetryPluginConfigBuilder.shouldRetryOnCall] predicate returns true,
 * after an [io.ktor.client.call.HttpClientCall] has been issued and the call couldn't be retried due to the retry configuration (e.g., maximum attempts reached).
 */
internal class RetryOnCallException : Exception("The retry-on-call predicate returned true after an HTTP client call was issued, but the call couldn't be retried due to the retry configuration (e.g., maximum attempts reached).")
