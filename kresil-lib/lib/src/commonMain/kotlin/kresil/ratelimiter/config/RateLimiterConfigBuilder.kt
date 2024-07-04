package kresil.ratelimiter.config

import kresil.core.builders.ConfigBuilder
import kresil.core.callbacks.ExceptionHandler
import kresil.core.delay.requireNonNegative
import kresil.core.delay.requirePositive
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.FixedWindowCounter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Builder for configuring a [RateLimiterConfig] instance.
 * Use [rateLimiterConfig] to create one.
 */
class RateLimiterConfigBuilder(
    override val baseConfig: RateLimiterConfig = defaultRateLimiterConfig,
) : ConfigBuilder<RateLimiterConfig> {

    private companion object {
        const val MIN_TOTAL_PERMITS = 1
        const val MIN_QUEUE_LENGTH = 0
    }

    // state
    private var onRejected: ExceptionHandler = baseConfig.onRejected
    private var algorithm: RateLimitingAlgorithm = baseConfig.algorithm

    fun algorithm(algorithm: RateLimitingAlgorithm) {
        checkAlgorithmConfig(algorithm)
        this.algorithm = algorithm
    }

    private fun checkAlgorithmConfig(algorithm: RateLimitingAlgorithm) {
        require(algorithm.totalPermits >= MIN_TOTAL_PERMITS) { "Total permits must be greater than or equal to $MIN_TOTAL_PERMITS" }
        algorithm.refreshPeriod.requirePositive("Refresh period")
        require(algorithm.queueLength >= MIN_QUEUE_LENGTH) { "Queue length must be greater than or equal to $MIN_QUEUE_LENGTH" }
        when (algorithm) {
            is FixedWindowCounter -> Unit // no additional checks
            is RateLimitingAlgorithm.TokenBucket -> {
                require(algorithm.tokensPerRefresh > 0) { "Tokens must be greater than 0" }
            }

            is RateLimitingAlgorithm.SlidingWindowCounter -> {
                require(algorithm.segments > 0) { "Segments must be greater than 0" }
            }
        }
    }

    /**
     * Configures the default duration a request will be placed in the queue if the rate limiter is full.
     * After this duration, the request will be rejected.
     * Should be non-negative.
     */
    var baseTimeoutDuration: Duration = baseConfig.baseTimeoutDuration
        set(value) {
            value.requireNonNegative("Base timeout")
            field = value
        }

    /**
     * Configures the exception handler that will be called when a request is rejected by the rate limiter.
     * By default, the exception handler will the caught exception.
     */
    fun onRejected(block: ExceptionHandler) {
        onRejected = block
    }

    override fun build() = RateLimiterConfig(
        algorithm = algorithm,
        baseTimeoutDuration = baseTimeoutDuration,
        onRejected = onRejected
    )
}

private val defaultRateLimiterConfig = RateLimiterConfig(
    algorithm = FixedWindowCounter(
        totalPermits = 100,
        refreshPeriod = 1.minutes,
        queueLength = 50
    ),
    baseTimeoutDuration = 10.seconds,
    onRejected = { throw it }
)
