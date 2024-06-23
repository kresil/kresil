package kresil.circuitbreaker.event

import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.state.CircuitBreakerState

/**
 * Represents all possible [CircuitBreaker] events that can be triggered in a [CircuitBreaker] mechanism.
 */
sealed class CircuitBreakerEvent {

    /**
     * Represents an event triggered when a **successful operation** is recorded.
     * @param failureRate The current failure rate of the circuit breaker.
     */
    data class RecordedSuccess(val failureRate: Double) : CircuitBreakerEvent()

    /**
     * Represents an event triggered when a **failed operation** is recorded.
     * @param failureRate The current failure rate of the circuit breaker.
     */
    data class RecordedFailure(val failureRate: Double) : CircuitBreakerEvent()

    /**
     * Represents an event triggered when the circuit breaker **transitions to a new state**.
     * @param fromState The state the circuit breaker is transitioning from.
     * @param toState The state the circuit breaker is transitioning to.
     * @param manual Whether the transition was triggered manually or not.
     */
    data class StateTransition(
        val fromState: CircuitBreakerState,
        val toState: CircuitBreakerState,
        val manual: Boolean = false
    ) : CircuitBreakerEvent()

    /**
     * Represents an event triggered when a **call is not permitted**.
     */
    data object CallNotPermitted : CircuitBreakerEvent()

    /**
     * Represents an event triggered when the circuit breaker **resets**.
     */
    data object Reset : CircuitBreakerEvent()

}
