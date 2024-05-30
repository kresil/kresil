package kresil.circuitbreaker.state

import kresil.circuitbreaker.CircuitBreaker

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
     */
    data object Open : CircuitBreakerState()

    /**
     * Represents the state where the circuit breaker is half-open, and **only a limited number of calls are allowed**.
     * @param nrOfCallsAttempted The number of calls attempted in the half-open state. If this number exceeds the
     * configured maximum number of calls allowed in the half-open state,
     * subsequent calls will be rejected as the state will transition back to the [Open] state.
     */
    data class HalfOpen(val nrOfCallsAttempted: Int) : CircuitBreakerState()
}
