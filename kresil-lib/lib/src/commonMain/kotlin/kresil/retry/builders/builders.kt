package kresil.retry.builders

import kresil.retry.config.RetryConfig
import kresil.retry.config.RetryConfigBuilder
import kresil.retry.Retry

/**
 * Creates a [RetryConfig] instance using the provided configuration.
 */
fun retryConfig(configure: RetryConfigBuilder.() -> Unit): RetryConfig {
    val builder = RetryConfigBuilder()
    builder.configure()
    return builder.build()
}

/**
 * Creates a [RetryConfig] instance using the default configuration.
 * @see [Retry]
 */
fun defaultRetryConfig(): RetryConfig = retryConfig {}
