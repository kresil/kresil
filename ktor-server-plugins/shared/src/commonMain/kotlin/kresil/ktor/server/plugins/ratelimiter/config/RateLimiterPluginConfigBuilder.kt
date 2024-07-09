package kresil.ktor.server.plugins.ratelimiter.config

import kresil.core.builders.ConfigBuilder
import kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin

/**
 * Builder for configuring the [KresilRateLimiterPlugin].
 */
class RateLimiterPluginConfigBuilder(override val baseConfig: RateLimiterPluginConfig) :
    ConfigBuilder<RateLimiterPluginConfig> {

    override fun build(): RateLimiterPluginConfig {
        TODO("Not yet implemented")
    }

}
