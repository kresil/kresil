package kresil.circuitbreaker.state

/**
 * Represents the aggregate state of a [CircuitBreakerStateReducer].
 * Besides the [state] property, which represents the current state of the circuit breaker,
 * it could also contain additional information.
 */
// TODO: change this to one class only, see RetryEvent.kt
data class CircuitBreakerReducerState(
    val state: CircuitBreakerState,
    val nrOfCallsInHalfOpenState: Int
)
