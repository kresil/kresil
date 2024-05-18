package kresil.circuitbreaker.state

import kresil.circuitbreaker.CircuitBreaker

/**
 * Represents the possible states of a circuit breaker.
 * @see CircuitBreaker
 */
enum class CircuitBreakerState {

    /**
     * Represents the state where the circuit breaker is closed and **all calls are allowed**.
     */
    CLOSED,

    /**
     * Represents the state where the circuit breaker is open and **all calls are blocked**.
     */
    OPEN,

    /**
     * Represents the state where the circuit breaker is half-open, and **only a limited number of calls are allowed**.
     */
    HALF_OPEN
}
