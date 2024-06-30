package kresil.ratelimiter.config

import kresil.core.builders.ConfigBuilder
import kresil.core.callbacks.ExceptionHandler
import kresil.core.delay.requireNonNegative
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

    /**
     * Configures the total number of permits that can be allowed in a given [refreshPeriod].
     * Should be greater than 0.
     */
    var totalPermits: Int = baseConfig.totalPermits
        set(value) {
            require(value >= MIN_TOTAL_PERMITS) { "Total permits must be greater than or equal to $MIN_TOTAL_PERMITS" }
            field = value
        }

    /**
     * Configures the time period in which the rate limiter will allow [totalPermits] permits.
     */
    var refreshPeriod: Duration = baseConfig.refreshPeriod
        set(value) {
            field = value
        }

    /**
     * Configures the maximum number of requests that can be queued per key when the rate limiter is exceeded.
     * Should be non-negative.
     * If set to 0, no requests will be queued and will be rejected immediately.
     */
    var queueLength: Int = baseConfig.queueLength
        set(value) {
            require(value >= MIN_QUEUE_LENGTH) { "Queue length must be greater than or equal to $MIN_QUEUE_LENGTH" }
            field = value
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
        totalPermits,
        refreshPeriod,
        queueLength,
        baseTimeoutDuration,
        onRejected
    )
}

private val defaultRateLimiterConfig = RateLimiterConfig(
    totalPermits = 100,
    refreshPeriod = 1.minutes,
    queueLength = 50,
    baseTimeoutDuration = 10.seconds,
    onRejected = { throw it }
)
