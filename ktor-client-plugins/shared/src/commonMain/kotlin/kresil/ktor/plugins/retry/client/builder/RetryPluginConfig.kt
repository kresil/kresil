package kresil.ktor.plugins.retry.client.builder

import kresil.retry.config.RetryConfig

data class RetryPluginConfig(
    val retryConfig: RetryConfig,
    val modifyRequestOnRetry: ModifyRequestOnRetry,
    val retryOnCallPredicate: RetryOnCallPredicate
)
