package kresil.circuitbreaker.state.reducer

import kresil.circuitbreaker.CircuitBreaker

/**
 * Represents the events that can be dispatched to the [CircuitBreaker] state machine reducer.
 * @see CircuitBreakerStateReducer
 */
enum class CircuitBreakerReducerEvent {

    /**
     * Represents an event where the protected operation execution was successful.
     */
    OPERATION_SUCCESS,

    /**
     * Represents an event where the protected operation execution failed.
     */
    OPERATION_FAILURE,
}
