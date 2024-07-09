package kresil.ktor.server.plugins.ratelimiter

import io.ktor.server.application.*
import io.ktor.util.logging.*
import kresil.ktor.server.plugins.ratelimiter.config.RateLimiterPluginConfig
import kresil.ktor.server.plugins.ratelimiter.config.RateLimiterPluginConfigBuilder
import kresil.ratelimiter.config.rateLimiterConfig

private val logger = KtorSimpleLogger("kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin")

val KresilRateLimiterPlugin = createApplicationPlugin(
    name = "KresilRateLimiterPlugin",
    createConfiguration = {
        RateLimiterPluginConfigBuilder(baseConfig = defaultRateLimiterPluginConfig)
    }
) {
    pluginConfig.apply {
        // TODO: Implement the rate limiter plugin
    }
}

private val defaultRateLimiterPluginConfig = RateLimiterPluginConfig(
    rateLimiterConfig = rateLimiterConfig {
        // TODO: Add default rate limiter configuration
    },
)
