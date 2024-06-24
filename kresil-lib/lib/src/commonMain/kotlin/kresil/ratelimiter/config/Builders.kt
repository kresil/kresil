package kresil.ratelimiter.config

import kresil.core.builders.mechanismConfigBuilder
import kresil.ratelimiter.RateLimiter

/**
 * Creates a [RateLimiterConfig] instance using the provided configuration.
 * @see [RateLimiter]
 */
fun rateLimiterConfig(configure: RateLimiterConfigBuilder.() -> Unit): RateLimiterConfig =
    mechanismConfigBuilder(RateLimiterConfigBuilder(), configure)

/**
 * Creates a [RateLimiterConfig] instance using the provided configuration which will be based on the received configuration.
 * @see [RateLimiter]
 */
fun rateLimiterConfig(
    baseConfig: RateLimiterConfig,
    configure: RateLimiterConfigBuilder.() -> Unit,
): RateLimiterConfig = mechanismConfigBuilder(RateLimiterConfigBuilder(baseConfig), configure)

/**
 * Creates a [RateLimiterConfig] instance using the default configuration.
 * @see [RateLimiter]
 */
fun defaultRateLimiterConfig(): RateLimiterConfig = rateLimiterConfig {}
