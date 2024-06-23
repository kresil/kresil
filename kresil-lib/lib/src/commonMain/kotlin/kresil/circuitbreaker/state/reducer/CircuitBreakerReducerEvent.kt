package kresil.circuitbreaker.state.reducer

import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.state.CircuitBreakerState.Closed
import kresil.circuitbreaker.state.CircuitBreakerState.HalfOpen
import kresil.circuitbreaker.state.CircuitBreakerState.Open

/**
 * Represents the events that can be dispatched to the [CircuitBreaker] state machine reducer.
 * @param isTransitionEvent Indicates whether the event is a possible state transition event.
 * @see CircuitBreakerStateReducer
 */
enum class CircuitBreakerReducerEvent(val isTransitionEvent: Boolean) {

    /**
     * Represents an event where the protected operation execution was successful.
     */
    OPERATION_SUCCESS(false),

    /**
     * Represents an event where the protected operation execution failed.
     */
    OPERATION_FAILURE(false),

    /**
     * Represents an event triggered to forcefully update the circuit breaker state in order to ensure that is
     * up-to-date.
     *
     * This event is particularly useful when the circuit breaker state is influenced by a timer or other type of side effect.
     * Instead of relying on the potentially outdated current state,
     * this event ensures that the state is refreshed and up-to-date upon retrieval.
     */
    FORCE_STATE_UPDATE(false),

    /**
     * Represents an event where the circuit breaker is asked to transition to the [Closed] state.
     */
    TRANSITION_TO_CLOSED_STATE(true),

    /**
     * Represents an event where the circuit breaker is asked to transition to the [Open] state.
     */
    TRANSITION_TO_OPEN_STATE(true),

    /**
     * Represents an event where the circuit breaker is asked to transition to the [HalfOpen] state.
     */
    TRANSITION_TO_HALF_OPEN_STATE(true),

    /**
     * Represents an event where the circuit breaker is asked to transition to the [Closed] state and clear the sliding window.
     */
    RESET(true);

}
