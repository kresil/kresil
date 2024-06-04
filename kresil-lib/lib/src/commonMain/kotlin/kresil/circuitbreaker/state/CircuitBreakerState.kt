package kresil.circuitbreaker.state

import kresil.circuitbreaker.CircuitBreaker
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

/**
 * Represents the possible states of a circuit breaker.
 * @see CircuitBreaker
 */
sealed class CircuitBreakerState {

    /**
     * Represents the state where the circuit breaker is closed and **all calls are allowed**.
     */
    data object Closed : CircuitBreakerState()

    /**
     * Represents the state where the circuit breaker is open and **all calls are blocked**.
     * @param delayDuration The duration for which the circuit breaker will remain in this state.
     * When exceeded, the circuit breaker will transition to the [HalfOpen] state.
     * @param startTimerMark The time mark at which the circuit breaker entered this state.
     */
    data class Open(
        val delayDuration: Duration,
        val startTimerMark: ComparableTimeMark,
    ) : CircuitBreakerState()

    /**
     * Represents the state where the circuit breaker is half-open, and **only a limited number of calls are allowed**.
     * @param nrOfCallsAttempted The number of calls attempted in the half-open state. If this number exceeds the
     * configured maximum number of calls allowed in the half-open state,
     * subsequent calls will be rejected as the state will transition back to the [Open] state.
     * @param startTimerMark The time mark at which the circuit breaker entered this state. Could be `null` if
     * the maximum wait duration in this state was configured to be `Duration.ZERO`.
     * Which means that the circuit breaker
     * will wait indefinitely in this state, until all permitted calls have been attempted.
     */
    data class HalfOpen(
        val nrOfCallsAttempted: Int,
        val startTimerMark: ComparableTimeMark?,
    ) : CircuitBreakerState()
}
