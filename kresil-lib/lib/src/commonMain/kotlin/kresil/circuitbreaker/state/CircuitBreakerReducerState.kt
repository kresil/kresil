package kresil.circuitbreaker.state

/**
 * Represents the aggregate state of a [CircuitBreakerStateReducer].
 * Besides the [state] property, which represents the current state of the circuit breaker,
 * it could also contain additional information.
 */
data class CircuitBreakerReducerState(
    val state: CircuitBreakerState,
    val nrOfCallsInHalfOpenState: Int
)