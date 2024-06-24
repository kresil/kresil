package kresil.ratelimiter.config

import kresil.core.builders.ConfigBuilder

/**
 * Builder for configuring a [RateLimiterConfig] instance.
 * Use [rateLimiterConfig] to create one.
 */
class RateLimiterConfigBuilder(
    override val baseConfig: RateLimiterConfig = defaultRateLimiterConfig,
) : ConfigBuilder<RateLimiterConfig> {

    override fun build(): RateLimiterConfig = TODO()
}

private val defaultRateLimiterConfig: RateLimiterConfig = TODO()
