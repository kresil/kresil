package kresil.retry.config

import kresil.retry.Retry

/**
 * Creates a [RetryConfig] instance using the provided configuration.
 * @see [Retry]
 */
fun retryConfig(configure: RetryConfigBuilder.() -> Unit): RetryConfig =
    RetryConfigBuilder().apply(configure).build()

/**
 * Creates a [RetryConfig] instance using the provided configuration which will be based on the received configuration.
 * * @see [Retry]
 */
fun retryConfig(baseConfig: RetryConfig, configure: RetryConfigBuilder.() -> Unit): RetryConfig =
    RetryConfigBuilder(baseConfig).apply(configure).build()

/**
 * Creates a [RetryConfig] instance using the default configuration.
 * @see [Retry]
 */
fun defaultRetryConfig(): RetryConfig = retryConfig {}
