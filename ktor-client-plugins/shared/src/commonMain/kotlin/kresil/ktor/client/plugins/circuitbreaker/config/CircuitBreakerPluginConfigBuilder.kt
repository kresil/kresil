package kresil.ktor.client.plugins.circuitbreaker.config

import io.ktor.client.statement.*
import kresil.circuitbreaker.config.CircuitBreakerConfigBuilder
import kresil.circuitbreaker.state.slidingwindow.SlidingWindowType
import kresil.circuitbreaker.state.slidingwindow.SlidingWindowType.COUNT_BASED
import kresil.circuitbreaker.state.CircuitBreakerState.HalfOpen
import kresil.circuitbreaker.state.CircuitBreakerState.Open
import kresil.core.builders.ConfigBuilder
import kresil.core.delay.provider.DelayProvider
import kresil.core.delay.strategy.DelayStrategy
import kresil.ktor.client.plugins.circuitbreaker.KresilCircuitBreakerPlugin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal typealias RecordResponsePredicate = (HttpResponse) -> Boolean

/**
 * Builder for configuring the [KresilCircuitBreakerPlugin].
 */
class CircuitBreakerPluginConfigBuilder(override val baseConfig: CircuitBreakerPluginConfig) :
    ConfigBuilder<CircuitBreakerPluginConfig> {

    private val baseCbreakerConfig = baseConfig.circuitBreakerConfig
    private val cbreakerConfigBuilder: CircuitBreakerConfigBuilder =
        CircuitBreakerConfigBuilder(baseCbreakerConfig)

    // state
    private var recordResponseAsFailurePredicate: RecordResponsePredicate = baseConfig.recordResponseAsFailurePredicate

    /**
     * Configures the rate in percentage (e.g., **0.5 for 50%**)
     * of calls recorded as failure that will trigger the circuit breaker
     * to transition to the [Open] state, if equalled or exceeded.
     *
     * Should be between `0.0` exclusive and `1.0` inclusive.
     */
    var failureRateThreshold: Double = baseCbreakerConfig.failureRateThreshold
        set(value) {
            cbreakerConfigBuilder.failureRateThreshold = value
            field = value
        }

    /**
     * Configures the number of calls that are allowed to be made in the [HalfOpen] state.
     * If this number is exceeded, further calls will be rejected.
     * If [maxWaitDurationInHalfOpenState] is set to `Duration.ZERO`, the circuit breaker will wait indefinitely
     * in the [HalfOpen] state until the permitted number of calls is reached.
     */
    var permittedNumberOfCallsInHalfOpenState: Int = baseCbreakerConfig.permittedNumberOfCallsInHalfOpenState
        set(value) {
            cbreakerConfigBuilder.permittedNumberOfCallsInHalfOpenState = value
            field = value
        }

    /**
     * Configures the maximum duration the circuit breaker will wait in the
     * [HalfOpen] state before transitioning to the [Open] state automatically.
     * If set to `Duration.ZERO`, the circuit breaker will wait indefinitely in the [HalfOpen] state
     * until [permittedNumberOfCallsInHalfOpenState] is reached.
     */
    var maxWaitDurationInHalfOpenState: Duration = baseCbreakerConfig.maxWaitDurationInHalfOpenState
        set(value) {
            cbreakerConfigBuilder.maxWaitDurationInHalfOpenState = value
            field = value
        }

    /**
     * Configures the sliding window used to record calls and calculate the failure rate.
     * @param size the size of the sliding window.
     * @param minimumThroughput the minimum number of calls that need to be recorded in the sliding window for the
     * failure rate to be calculated. Even if the [failureRateThreshold] is exceeded, the circuit breaker will not
     * transition to the [Open] state if the number of calls recorded in the sliding window is less than this value.
     * @param type the type of the sliding window. See [SlidingWindowType] for more information about the available types.
     */
    fun slidingWindow(size: Int, minimumThroughput: Int = 100, type: SlidingWindowType = COUNT_BASED) {
        cbreakerConfigBuilder.slidingWindow(size, minimumThroughput, type)
    }

    /**
     * Configures the circuit breaker delay strategy to use no delay between transitions from [Open] to [HalfOpen].
     * @see [constantDelayInOpenState]
     * @see [linearDelayInOpenState]
     * @see [exponentialDelayInOpenState]
     * @see [customDelayInOpenState]
     * @see [customDelayProviderInOpenState]
     */
    fun noDelayInOpenState() {
        cbreakerConfigBuilder.noDelayInOpenState()
    }

    /**
     * Configures the circuit breaker delay strategy to use a constant delay between transitions from [Open] to [HalfOpen].
     * @param delay the constant delay between transitions.
     * @see [noDelayInOpenState]
     * @see [linearDelayInOpenState]
     * @see [exponentialDelayInOpenState]
     * @see [customDelayInOpenState]
     * @see [customDelayProviderInOpenState]
     */
    fun constantDelayInOpenState(delay: Duration) {
        cbreakerConfigBuilder.constantDelayInOpenState(delay)
    }

    /**
     * Configures the circuit breaker delay strategy
     * to use a linear backoff algorithm to calculate the next delay duration between transitions from [Open] to [HalfOpen].
     * - `initialDelay + (initialDelay * (attempt - 1) * multiplier)`,
     * where `attempt` is the number of times the circuit breaker is in the [Open] state in one cycle.
     *
     * Example:
     * ```
     * linear(500.milliseconds, 1.0, 4.seconds)
     * // Delay between transitions will be as follows:
     * // [500ms, 1s, 1.5s, 2s, 2.5s, 3s, 3.5s, 4s, 4s, 4s, ...]
     * ```
     * @param initialDelay the initial delay before the first transition.
     * @param multiplier the multiplier to increase the delay between transitions.
     * @param maxDelay the maximum delay between transitions. Used as a safety net to prevent infinite delays.
     * @see [noDelayInOpenState]
     * @see [constantDelayInOpenState]
     * @see [exponentialDelayInOpenState]
     * @see [customDelayInOpenState]
     * @see [customDelayProviderInOpenState]
     */
    fun linearDelayInOpenState(
        initialDelay: Duration = 500L.milliseconds,
        multiplier: Double = 1.0,
        maxDelay: Duration = 1.minutes,
    ) {
        cbreakerConfigBuilder.linearDelayInOpenState(initialDelay, multiplier, maxDelay)
    }

    /**
     * Configures the circuit breaker delay strategy
     * to use an exponential backoff algorithm to calculate the next delay duration between transitions from [Open] to [HalfOpen].
     * The algorithm is based on the formula:
     * - `(initialDelay * multiplier^(attempt - 1))`,
     * where `attempt` is the number of times the circuit breaker is in the [Open] state in one cycle.
     *
     * Example:
     * ```
     * exponential(500.milliseconds, 2.0, 1.minutes)
     * // Delay between transitions will be as follows:
     * // [500ms, 1s, 2s, 4s, 8s, 16s, 32s, 1m, 1m, 1m, ...]
     * ```
     * @param initialDelay the initial delay before the first transition.
     * @param multiplier the multiplier to increase the delay between transitions.
     * @param maxDelay the maximum delay between transitions. Used as a safety net to prevent infinite delays.
     * @see [noDelayInOpenState]
     * @see [constantDelayInOpenState]
     * @see [linearDelayInOpenState]
     * @see [customDelayInOpenState]
     * @see [customDelayProviderInOpenState]
     */
    fun exponentialDelayInOpenState(
        initialDelay: Duration = 500L.milliseconds,
        multiplier: Double = 2.0,
        maxDelay: Duration = 1.minutes,
    ) {
        cbreakerConfigBuilder.exponentialDelayInOpenState(initialDelay, multiplier, maxDelay)
    }

    /**
     * Configures the circuit breaker delay strategy
     * to use a custom delay between transitions from [Open] to [HalfOpen],
     * based on the current attempt and additional context.
     * The attempt is the number of times the circuit breaker transitioned from [Open] to [HalfOpen].
     *
     * Example:
     * ```
     * customDelayInOpenState { attempt ->
     *      attempt % 2 == 0 -> 1.seconds
     *      else -> 3.seconds
     * }
     * ```
     * Where:
     * - `attempt` is the current retry attempt. Starts at **1**.
     * @param delayStrategyInOpenState the custom delay strategy to use.
     * @see [noDelayInOpenState]
     * @see [constantDelayInOpenState]
     * @see [linearDelayInOpenState]
     * @see [exponentialDelayInOpenState]
     * @see [customDelayProviderInOpenState]
     **/
    fun customDelayInOpenState(delayStrategyInOpenState: DelayStrategy) {
        cbreakerConfigBuilder.customDelayInOpenState(delayStrategyInOpenState)
    }

    /**
     * Configures the circuit breaker delay strategy to use a custom delay provider between transitions from [Open] to [HalfOpen].
     * In contrast to [customDelayInOpenState], this method enables caller control over the delay provider (which is the
     * [kotlinx.coroutines.delay] by default) and optional additional state between transitions.
     * See [DelayProvider] for more information.
     * @param delayProvider the custom delay provider to use.
     * @see [noDelayInOpenState]
     * @see [constantDelayInOpenState]
     * @see [linearDelayInOpenState]
     * @see [exponentialDelayInOpenState]
     * @see [customDelayInOpenState]
     */
    fun customDelayProviderInOpenState(delayProvider: DelayProvider) {
        cbreakerConfigBuilder.customDelayProviderInOpenState(delayProvider)
    }

    /**
     * Configures a predicate to record the response of a call.
     * The predicate should return `true` if the response is to be considered a failure; `false` otherwise.
     */
    fun recordFailure(predicate: RecordResponsePredicate) {
        recordResponseAsFailurePredicate = predicate
    }

    /**
     * Determines whether to record as failure the server responses with status codes in the range of 500..599.
     */
    fun recordFailureOnServerErrors() {
        recordFailure { response ->
            response.status.value in 500..599
        }
    }

    override fun build(): CircuitBreakerPluginConfig {
        val cBreakerConfig = cbreakerConfigBuilder.build()
        return CircuitBreakerPluginConfig(
            circuitBreakerConfig = cBreakerConfig,
            recordResponseAsFailurePredicate = recordResponseAsFailurePredicate
        )
    }

}
