package kresil.ktor.server.plugins.ratelimiter

import io.ktor.server.application.*
import io.ktor.util.logging.*
import kresil.ktor.server.plugins.ratelimiter.config.RateLimiterPluginConfig
import kresil.ktor.server.plugins.ratelimiter.config.RateLimiterPluginConfigBuilder
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.FixedWindowCounter
import kresil.ratelimiter.config.rateLimiterConfig
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KtorSimpleLogger("kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin")

val KresilRateLimiterPlugin = createApplicationPlugin(
    name = "KresilRateLimiterPlugin",
    createConfiguration = {
        RateLimiterPluginConfigBuilder(baseConfig = defaultRateLimiterPluginConfig)
    }
) {
    pluginConfig.apply {
        // TODO: Implement the rate limiter plugin
        // TODO: what handler should be used for rate limiting?
    }
}

private val defaultRateLimiterPluginConfig = RateLimiterPluginConfig(
    rateLimiterConfig = rateLimiterConfig {
        algorithm(
            FixedWindowCounter(
                totalPermits = 1000,
                replenishmentPeriod = 1.minutes,
                queueLength = 0
            )
        )
        baseTimeoutDuration = 3.seconds
        onRejected { throw it }
    },
)
