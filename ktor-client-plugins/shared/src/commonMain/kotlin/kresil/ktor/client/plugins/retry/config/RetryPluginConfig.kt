package kresil.ktor.client.plugins.retry.config

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kresil.ktor.client.plugins.retry.KresilRetryPlugin
import kresil.retry.Retry
import kresil.retry.config.RetryConfig

/**
 * Predicate to determine if an HTTP call should be retried based on the request and response.
 * @return `true` if the call should be retried, `false` otherwise.
 */
internal typealias RetryOnCallPredicate = (HttpRequest, HttpResponse) -> Boolean

/**
 * Callback to modify the request between retries.
 */
internal typealias ModifyRequestOnRetry = (HttpRequestBuilder, attempt: Int) -> Unit

/**
 * Configuration for the [KresilRetryPlugin].
 * @param retryConfig The configuration for the Kresil [Retry] mechanism.
 * @param modifyRequestOnRetry A function that modifies the request before each retry.
 * @param retryOnCallPredicate A predicate that determines whether a call should be retried after it has been issued.
 */
data class RetryPluginConfig(
    val retryConfig: RetryConfig,
    val modifyRequestOnRetry: ModifyRequestOnRetry,
    val retryOnCallPredicate: RetryOnCallPredicate,
)
