package kresil.ktor.server.plugins.ratelimiter.config

import kresil.core.builders.ConfigBuilder
import kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm
import kresil.ratelimiter.config.RateLimiterConfigBuilder
import kotlin.time.Duration

/**
 * Builder for configuring the [KresilRateLimiterPlugin].
 */
class RateLimiterPluginConfigBuilder(override val baseConfig: RateLimiterPluginConfig) :
    ConfigBuilder<RateLimiterPluginConfig> {

    private val rateLimiterConfig = baseConfig.rateLimiterConfig
    private val rateLimiterConfigBuilder = RateLimiterConfigBuilder(rateLimiterConfig)

    /**
     * Sets the rate limiting algorithm.
     * See [RateLimitingAlgorithm] for more details.
     */
    fun algorithm(algorithm: RateLimitingAlgorithm) {
        rateLimiterConfigBuilder.algorithm(algorithm)
    }

    /**
     * Configures the default duration a request will be placed in the queue if the request is rate-limited.
     * After this duration, the request will be rejected.
     * Should be non-negative.
     */
    var baseTimeoutDuration: Duration = rateLimiterConfigBuilder.baseTimeoutDuration
        set(value) {
            rateLimiterConfigBuilder.baseTimeoutDuration = value
            field = value
        }

    override fun build() = RateLimiterPluginConfig(
        rateLimiterConfig = rateLimiterConfigBuilder.build()
    )

}
