package kresil.ktor.plugins.retry.client.exceptions

import kresil.ktor.plugins.retry.client.builder.RetryPluginBuilder

/**
 * Represents an internal exception indicating that a retry is attempted due to the satisfaction of the [RetryPluginBuilder.shouldRetryOnCall] predicate.
 */
internal class RetryOnCallException : Exception()
