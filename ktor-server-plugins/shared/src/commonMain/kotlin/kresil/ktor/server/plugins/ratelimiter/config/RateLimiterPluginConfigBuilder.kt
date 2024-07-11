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
    private var keyResolver: KeyResolver = baseConfig.keyResolver
    private var onRejectedCall: OnRejectedCall = baseConfig.onRejectedCall
    private var onSuccessCall: OnSuccessCall = baseConfig.onSuccessCall
    private var excludeFromRateLimiting: ExcludeFromRateLimiting = baseConfig.excludeFromRateLimiting
    private var interceptPhase: InterceptPhase = baseConfig.interceptPhase

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

    /**
     * Sets the function to resolve the key for rate limiting from the application call.
     */
    fun keyResolver(keyResolver: KeyResolver) {
        this.keyResolver = keyResolver
    }

    /**
     * Sets the callback to handle requests that are rejected due to rate limiting.
     */
    fun onRejectedCall(onRejectedCall: OnRejectedCall) {
        this.onRejectedCall = onRejectedCall
    }

    /**
     * Sets the callback to handle successful requests.
     */
    fun onSuccessCall(onSuccessCall: OnSuccessCall) {
        this.onSuccessCall = onSuccessCall
    }

    /**
     * Sets the function to determine if a request should be excluded from rate limiting.
     */
    fun excludeFromRateLimiting(excludeFromRateLimiting: ExcludeFromRateLimiting) {
        this.excludeFromRateLimiting = excludeFromRateLimiting
    }

    /**
     * Sets the phase in the application pipeline where rate limiting is applied.
     */
    fun interceptPhase(interceptPhase: InterceptPhase) {
        this.interceptPhase = interceptPhase
    }

    override fun build() = RateLimiterPluginConfig(
        rateLimiterConfig = rateLimiterConfigBuilder.build(),
        keyResolver = keyResolver,
        onRejectedCall = onRejectedCall,
        onSuccessCall = onSuccessCall,
        excludeFromRateLimiting = excludeFromRateLimiting,
        interceptPhase = interceptPhase
    )

}
