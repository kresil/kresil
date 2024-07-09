package kresil.ktor.server.plugins.ratelimiter.config

import kresil.ratelimiter.config.RateLimiterConfig
import kresil.ratelimiter.RateLimiter
import kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin

/**
 * Configuration for the [KresilRateLimiterPlugin].
 * @param rateLimiterConfig The configuration for the Kresil [RateLimiter] mechanism.
 */
data class RateLimiterPluginConfig(
    val rateLimiterConfig: RateLimiterConfig
)
