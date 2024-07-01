package kresil.retry.config

import kresil.core.builders.mechanismConfigBuilder
import kresil.retry.Retry

/**
 * Creates a [RetryConfig] instance using the provided configuration.
 * @see [Retry]
 */
fun retryConfig(configure: RetryConfigBuilder.() -> Unit): RetryConfig =
    mechanismConfigBuilder(RetryConfigBuilder(), configure)

/**
 * Creates a [RetryConfig] instance using the provided configuration which will be based on the received configuration.
 * @see [Retry]
 */
fun retryConfig(baseConfig: RetryConfig, configure: RetryConfigBuilder.() -> Unit): RetryConfig =
    mechanismConfigBuilder(RetryConfigBuilder(baseConfig), configure)

/**
 * Creates a [RetryConfig] instance using the default configuration.
 * @see [Retry]
 */
fun defaultRetryConfig(): RetryConfig = retryConfig {}
