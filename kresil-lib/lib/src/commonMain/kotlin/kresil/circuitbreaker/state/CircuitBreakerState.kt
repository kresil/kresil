package kresil.circuitbreaker.state

enum class CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
