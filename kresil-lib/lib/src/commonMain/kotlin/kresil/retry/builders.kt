package kresil.retry

import kresil.retry.config.RetryConfig
import kresil.retry.config.RetryConfigBuilder

fun retryConfig(configure: RetryConfigBuilder.() -> Unit): RetryConfig {
    val builder = RetryConfigBuilder()
    builder.configure()
    return builder.build()
}

fun defaultRetryConfig(): RetryConfig = retryConfig {}
