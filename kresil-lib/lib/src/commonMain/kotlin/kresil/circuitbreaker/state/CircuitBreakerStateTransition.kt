package kresil.circuitbreaker.state

internal enum class CircuitBreakerStateTransition(
    from: CircuitBreakerState,
    to: CircuitBreakerState,
) {
    CLOSED_TO_OPEN(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN),
    OPEN_TO_HALF_OPEN(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN),
    HALF_OPEN_TO_OPEN(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN),
    HALF_OPEN_TO_CLOSED(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED),
}
