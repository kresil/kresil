package kresil.ktor.plugins.retry.client.exceptions

import kresil.ktor.plugins.retry.client.builder.RetryPluginBuilder

/**
 * Exception thrown when the [RetryPluginBuilder.shouldRetryOnCall] predicate returns true,
 * after an [io.ktor.client.call.HttpClientCall] has been issued.
 */
internal class RetryOnCallException : Exception("The retry-on-call predicate returned true after an HTTP client call was issued, but the call couldn't be retried due to the retry configuration (e.g., maximum attempts reached).")