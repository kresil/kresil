package kresil.ktor.plugins.retry.client.config

import kresil.retry.Retry
import kresil.retry.config.RetryConfig
import kresil.ktor.plugins.retry.client.KresilRetryPlugin

/**
 * Configuration for the [KresilRetryPlugin].
 * @param retryConfig The configuration for the Kresil [Retry] mechanism.
 * @param modifyRequestOnRetry A function that modifies the request before each retry.
 * @param retryOnCallPredicate A predicate that determines whether a call should be retried after it has been issued.
 */
data class RetryPluginConfig(
    val retryConfig: RetryConfig,
    val modifyRequestOnRetry: ModifyRequestOnRetry,
    val retryOnCallPredicate: RetryOnCallPredicate
)
