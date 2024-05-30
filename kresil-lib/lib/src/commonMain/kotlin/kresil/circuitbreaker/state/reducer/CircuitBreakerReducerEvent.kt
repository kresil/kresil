package kresil.circuitbreaker.state.reducer

import kresil.circuitbreaker.CircuitBreaker

/**
 * Represents the events that can be dispatched to the [CircuitBreaker] state machine reducer.
 * @see CircuitBreakerStateReducer
 */
enum class CircuitBreakerReducerEvent {

    /**
     * Represents a successful operation that should be recorded.
     */
    OPERATION_SUCCESS,

    /**
     * Represents a failed operation that should be recorded.
     */
    OPERATION_FAILURE,
}
