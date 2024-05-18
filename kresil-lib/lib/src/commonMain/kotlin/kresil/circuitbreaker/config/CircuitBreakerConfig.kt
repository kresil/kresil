package kresil.circuitbreaker.config

import kresil.core.callbacks.OnExceptionPredicate
import kresil.core.callbacks.OnResultPredicate
import kotlin.time.Duration
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.state.CircuitBreakerState.*

/**
 * Represents a [CircuitBreaker] configuration.
 * @param failureRateThreshold the rate in percentage (e.g. **0.5 for 50%**)
 * of calls
 * recorded as failure that will trigger the circuit breaker to transition to the [OPEN] state if equalled or exceeded.
 * @param slidingWindowSize the size of the sliding window used to calculate the failure rate.
 * @param minimumThroughput the minimum number of calls that need to be recorded in the sliding window for the
 * failure rate to be calculated.
 * Even if the [failureRateThreshold] is exceeded, the circuit breaker will not transition to the [OPEN] state if the
 * number of calls recorded in the sliding window is less than this value.
 * @param permittedNumberOfCallsInHalfOpenState the number of calls that are allowed to be made in the [HALF_OPEN] state.
 * If this number is exceeded, all subsequent calls will be rejected.
 * If one of the calls made in the [HALF_OPEN] state fails, the circuit breaker will transition back to the [OPEN] state.
 * @param waitDurationInOpenState the duration the circuit breaker will wait in the [OPEN] state before transitioning to the [HALF_OPEN] state.
 * @param waitDurationInHalfOpenState the duration the circuit breaker will wait in the [HALF_OPEN] state before transitioning to the [CLOSED] state.
 * @param recordExceptionPredicate a predicate that determines whether an exception thrown by the underlying operation should be recorded as a failure, and as such, increase the failure rate.
 * @param recordSuccessAsFailurePredicate a predicate that determines whether the result of the underlying operation should be recorded as a failure,
 * and as such, increase the failure rate.
 */
data class CircuitBreakerConfig(
    val failureRateThreshold: Double,
    // TODO: add support for count and time-based sliding windows
    val slidingWindowSize: Int,
    val minimumThroughput: Int,
    val permittedNumberOfCallsInHalfOpenState: Int,
    val waitDurationInOpenState: Duration,
    val waitDurationInHalfOpenState: Duration,
    val recordExceptionPredicate: OnExceptionPredicate,
    val recordSuccessAsFailurePredicate: OnResultPredicate,
)
