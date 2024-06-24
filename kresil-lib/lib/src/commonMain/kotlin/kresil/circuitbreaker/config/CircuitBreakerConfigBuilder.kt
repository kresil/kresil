package kresil.circuitbreaker.config

import kresil.circuitbreaker.state.slidingwindow.SlidingWindow
import kresil.circuitbreaker.state.slidingwindow.SlidingWindowType
import kresil.circuitbreaker.state.slidingwindow.SlidingWindowType.COUNT_BASED
import kresil.circuitbreaker.state.CircuitBreakerState.HalfOpen
import kresil.circuitbreaker.state.CircuitBreakerState.Open
import kresil.core.builders.ConfigBuilder
import kresil.core.callbacks.OnExceptionPredicate
import kresil.core.callbacks.OnResultPredicate
import kresil.core.delay.provider.DelayProvider
import kresil.core.delay.requireNonNegative
import kresil.core.delay.strategy.CtxDelayStrategy
import kresil.core.delay.strategy.DelayStrategy
import kresil.core.delay.strategy.DelayStrategyOptions
import kresil.core.delay.strategy.DelayStrategyOptions.toEmptyCtxDelayStrategy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Builder for configuring a [CircuitBreakerConfig] instance.
 * Use [circuitBreakerConfig] to create one.
 */
class CircuitBreakerConfigBuilder(
    override val baseConfig: CircuitBreakerConfig = defaultCircuitBreakerConfig,
) : ConfigBuilder<CircuitBreakerConfig> {

    private companion object {
        const val MAX_FAILURE_RATE_THRESHOLD = 1.0
        const val MIN_FAILURE_RATE_THRESHOLD = 0.0
    }

    // delay strategy options
    private val delayStrategyInOpenStateOptions = DelayStrategyOptions

    // state
    private var recordExceptionPredicate: OnExceptionPredicate = baseConfig.recordExceptionPredicate
    private var recordResultPredicate: OnResultPredicate = baseConfig.recordResultPredicate
    private var delayStrategyInOpenState: CtxDelayStrategy<Unit> = baseConfig.delayStrategyInOpenState
    private var slidingWindow: SlidingWindow = baseConfig.slidingWindow

    /**
     * Configures the rate in percentage (e.g., **0.5 for 50%**)
     * of calls recorded as failure that will trigger the circuit breaker
     * to transition to the [Open] state, if equalled or exceeded.
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
     * Configures the number of calls that are allowed to be made in the [HalfOpen] state.
     * If this number is exceeded, further calls will be rejected.
     * If [maxWaitDurationInHalfOpenState] is set to `Duration.ZERO`, the circuit breaker will wait indefinitely
     * in the [HalfOpen] state until the permitted number of calls is reached.
     */
    var permittedNumberOfCallsInHalfOpenState: Int = baseConfig.permittedNumberOfCallsInHalfOpenState
        set(value) {
            require(value > 0) { "Permitted number of calls in ${HalfOpen::class.simpleName} state must be greater than 0" }
            field = value
        }

    /**
     * Configures the maximum duration the circuit breaker will wait in the
     * [HalfOpen] state before transitioning to the [Open] state automatically.
     * If set to `Duration.ZERO`, the circuit breaker will wait indefinitely in the [HalfOpen] state
     * until [permittedNumberOfCallsInHalfOpenState] is reached.
     */
    var maxWaitDurationInHalfOpenState: Duration = baseConfig.maxWaitDurationInHalfOpenState
        set(value) {
            value.requireNonNegative("${HalfOpen::class.simpleName} state")
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
        require(size > 0) { "Sliding window size must be greater than 0" }
        require(minimumThroughput > 0) { "Minimum throughput must be greater than 0" }
        slidingWindow = SlidingWindow(size, minimumThroughput, type)
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
        delayStrategyInOpenState = delayStrategyInOpenStateOptions
            .noDelay()
            .toEmptyCtxDelayStrategy()
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
    fun constantDelayInOpenState(
        delay: Duration,
    ) {
        delayStrategyInOpenState = delayStrategyInOpenStateOptions
            .constant(delay)
            .toEmptyCtxDelayStrategy()
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
        delayStrategyInOpenState = delayStrategyInOpenStateOptions
            .linear(initialDelay, multiplier, maxDelay)
            .toEmptyCtxDelayStrategy()
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
        delayStrategyInOpenState = delayStrategyInOpenStateOptions
            .exponential(initialDelay, maxDelay, multiplier)
            .toEmptyCtxDelayStrategy()
    }

    /**
     * Configures the circuit breaker delay strategy
     * to use a custom delay between transitions from [Open] to [HalfOpen],
     * based on the current attempt and additional context.
     * The current attempt is the number of times the circuit breaker is in the [Open] state in one cycle.
     *
     * Example:
     * ```
     * customDelayInOpenState { attempt ->
     *    if (attempt % 2 == 0) 1.seconds
     *    else 2.seconds
     * }
     * ```
     * @param delayStrategyInOpenState the custom delay strategy to use.
     * @see [noDelayInOpenState]
     * @see [constantDelayInOpenState]
     * @see [linearDelayInOpenState]
     * @see [exponentialDelayInOpenState]
     * @see [customDelayProviderInOpenState]
     **/
    fun customDelayInOpenState(delayStrategyInOpenState: DelayStrategy) {
        this.delayStrategyInOpenState = delayStrategyInOpenState.toEmptyCtxDelayStrategy()
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
        delayStrategyInOpenState = delayStrategyInOpenStateOptions
            .customProvider(delayProvider)
            .toEmptyCtxDelayStrategy()
    }

    /**
     * Configures the predicate that determines whether an exception should be recorded as a failure,
     * and as such, increase the failure rate.
     */
    fun recordExceptionPredicate(predicate: OnExceptionPredicate) {
        recordExceptionPredicate = predicate
    }

    /**
     * Configures the predicate that determines whether a result of an operation
     * should be recorded as a failure, and as such, increase the failure rate.
     */
    fun recordResultPredicate(predicate: OnResultPredicate) {
        recordResultPredicate = predicate
    }

    override fun build() = CircuitBreakerConfig(
        failureRateThreshold,
        slidingWindow,
        permittedNumberOfCallsInHalfOpenState,
        delayStrategyInOpenState,
        maxWaitDurationInHalfOpenState,
        recordExceptionPredicate,
        recordResultPredicate
    )
}

private val defaultCircuitBreakerConfig = CircuitBreakerConfig(
    failureRateThreshold = 0.5,
    slidingWindow = SlidingWindow(100, 100, COUNT_BASED),
    permittedNumberOfCallsInHalfOpenState = 10,
    delayStrategyInOpenState = DelayStrategyOptions
        .constant(1.minutes, 0.0)
        .toEmptyCtxDelayStrategy(),
    maxWaitDurationInHalfOpenState = ZERO,
    recordExceptionPredicate = { true },
    recordResultPredicate = { false },
)
