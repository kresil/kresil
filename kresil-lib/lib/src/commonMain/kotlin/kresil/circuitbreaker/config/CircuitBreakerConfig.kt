package kresil.circuitbreaker.config

import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.state.slidingwindow.SlidingWindow
import kresil.circuitbreaker.state.CircuitBreakerState.*
import kresil.core.callbacks.OnExceptionPredicate
import kresil.core.callbacks.OnResultPredicate
import kresil.core.delay.strategy.CtxDelayStrategy
import kotlin.time.Duration

/**
 * Represents a [CircuitBreaker] configuration.
 * @param failureRateThreshold the rate in percentage (e.g., **0.5 for 50%**)
 * of calls
 * recorded as failure that will trigger the circuit breaker to transition to the [Open] state if equalled or exceeded.
 * @param slidingWindow the sliding window used to record the result (success or failure) of calls and calculate the failure rate.
 * Even if the [failureRateThreshold] is exceeded, the circuit breaker will not transition to the [Open] state if the
 * number of calls recorded in the sliding window is less than this value.
 * @param permittedNumberOfCallsInHalfOpenState the number of calls that are allowed to be made in the [HalfOpen] state.
 * If this number is exceeded, all subsequent calls will be rejected.
 * If one of the calls made in the [HalfOpen] state fails, the circuit breaker will transition back to the [Open] state.
 * @param delayStrategyInOpenState represents the delay strategy that determines next delay duration in [Open] before switching to [HalfOpen], based on the current transition attempt and additional context.
 * This strategy can be used for complex delay strategies that can be used to further delay the transition
 * (e.g., to give the operation, and the underlying system, more time to recover after a failure in [HalfOpen]).
 * @param maxWaitDurationInHalfOpenState the duration the circuit breaker will wait in the [HalfOpen] state before transitioning to the [Closed] state.
 * @param recordExceptionPredicate a predicate that determines whether an exception thrown by the underlying operation should be recorded as a failure, and as such, increase the failure rate.
 * @param recordResultPredicate a predicate that determines whether the result of the underlying operation should be recorded as a failure,
 * and as such, increase the failure rate.
 */
data class CircuitBreakerConfig(
    val failureRateThreshold: Double,
    val slidingWindow: SlidingWindow,
    val permittedNumberOfCallsInHalfOpenState: Int,
    val delayStrategyInOpenState: CtxDelayStrategy<Unit>,
    val maxWaitDurationInHalfOpenState: Duration,
    val recordExceptionPredicate: OnExceptionPredicate,
    val recordResultPredicate: OnResultPredicate,
)
