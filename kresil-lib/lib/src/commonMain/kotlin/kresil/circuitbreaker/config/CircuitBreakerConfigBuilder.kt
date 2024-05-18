package kresil.circuitbreaker.config

import kresil.circuitbreaker.state.CircuitBreakerState.*
import kresil.core.builders.ConfigBuilder
import kresil.core.callbacks.OnExceptionPredicate
import kresil.core.callbacks.OnResultPredicate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Builder for configuring a [CircuitBreakerConfig] instance.
 * Use [circuitBreakerConfig] to create one.
 */
class CircuitBreakerConfigBuilder internal constructor(
    override val baseConfig: CircuitBreakerConfig = defaultCircuitBreakerConfig,
) : ConfigBuilder<CircuitBreakerConfig> {

    companion object {
        const val MAX_FAILURE_RATE_THRESHOLD = 1.0
        const val MIN_FAILURE_RATE_THRESHOLD = 0.0
    }

    // state
    private var recordExceptionPredicate: OnExceptionPredicate = baseConfig.recordExceptionPredicate
    private var recordSuccessAsFailurePredicate: OnResultPredicate = baseConfig.recordSuccessAsFailurePredicate

    /**
     * Configures the rate in percentage (e.g. **0.5 for 50%**)
     * of calls recorded as failure that will trigger the circuit breaker
     * to transition to the [OPEN] state, if equalled or exceeded.
     *
     * Should be between `0.0` exclusive and `1.0` inclusive.
     */
    var failureRateThreshold: Double = baseConfig.failureRateThreshold
        set(value) {
            require(value > MIN_FAILURE_RATE_THRESHOLD && value <= MAX_FAILURE_RATE_THRESHOLD) {
                "Failure rate threshold must be between ${MIN_FAILURE_RATE_THRESHOLD.toInt()} exclusive and ${MAX_FAILURE_RATE_THRESHOLD.toInt()} inclusive"
            }
            field = value
        }

    /**
     * Configures the size of the sliding window used to record calls and calculate the failure rate.
     *
     * Should be greater than `0`.
     */
    var slidingWindowSize: Int = baseConfig.slidingWindowSize
        set(value) {
            require(value > 0) { "Sliding window size must be greater than 0" }
            field = value
        }

    /**
     * Configures the minimum number of calls that need to be recorded in the sliding window for the
     * failure rate to be calculated.
     * Even if the [failureRateThreshold] is exceeded, the circuit breaker will not transition to the [OPEN] state if the
     * number of calls recorded in the sliding window is less than this value.
     *
     * Should be greater than `0`.
     */
    var minimumThroughput: Int = baseConfig.minimumThroughput
        set(value) {
            require(value > 0) { "Minimum throughput must be greater than 0" }
            field = value
        }

    /**
     * Configures the number of calls that are allowed to be made in the [HALF_OPEN] state.
     * If this number is exceeded, further calls will be rejected.
     * If one of the calls fails, the circuit breaker transitions back to the [OPEN] state.
     */
    var permittedNumberOfCallsInHalfOpenState: Int = baseConfig.permittedNumberOfCallsInHalfOpenState
        set(value) {
            require(value >= 0) { "Permitted number of calls in $HALF_OPEN state must be greater than or equal to 0" }
            field = value
        }

    /**
     * Configures the duration the circuit breaker will wait in the
     * [OPEN] state before transitioning to the [HALF_OPEN] state automatically.
     */
    var waitDurationInOpenState: Duration = baseConfig.waitDurationInOpenState
        set(value) {
            requireNonNegativeDuration(value, "$OPEN state")
            field = value
        }

    /**
     * Configures the duration the circuit breaker will wait in the
     * [HALF_OPEN] state before transitioning to the [CLOSED] state automatically.
     */
    var waitDurationInHalfOpenState: Duration = baseConfig.waitDurationInHalfOpenState
        set(value) {
            requireNonNegativeDuration(value, "$HALF_OPEN state")
            field = value
        }

    /**
     * Configures the predicate that determines whether an exception should be recorded as a failure,
     * and as such, increase the failure rate.
     */
    fun recordExceptionPredicate(predicate: OnExceptionPredicate) {
        recordExceptionPredicate = predicate
    }

    /**
     * Configures the predicate that determines whether a result of a successful operation
     * should be recorded as a failure, and as such, increase the failure rate.
     */
    fun recordSuccessAsFailurePredicate(predicate: OnResultPredicate) {
        recordSuccessAsFailurePredicate = predicate
    }

    override fun build() = CircuitBreakerConfig(
        failureRateThreshold,
        slidingWindowSize,
        minimumThroughput,
        permittedNumberOfCallsInHalfOpenState,
        waitDurationInOpenState,
        waitDurationInHalfOpenState,
        recordExceptionPredicate,
        recordSuccessAsFailurePredicate
    )

    /**
     * Validates that the duration is equal to or greater than 0.
     * @param duration the duration to validate.
     * @param qualifier the qualifier to use in the exception message.
     * @throws IllegalArgumentException if the duration is less than or equal to 0
     */
    @Throws(IllegalArgumentException::class)
    private fun requireNonNegativeDuration(duration: Duration, qualifier: String) {
        require(duration >= Duration.ZERO) { "$qualifier duration must be greater than or equal to 0" }
    }

}

private val defaultCircuitBreakerConfig = CircuitBreakerConfig(
    failureRateThreshold = 0.5,
    slidingWindowSize = 100,
    minimumThroughput = 100,
    permittedNumberOfCallsInHalfOpenState = 10,
    waitDurationInOpenState = 60.seconds,
    waitDurationInHalfOpenState = 25.seconds,
    recordExceptionPredicate = { true },
    recordSuccessAsFailurePredicate = { false },
)
