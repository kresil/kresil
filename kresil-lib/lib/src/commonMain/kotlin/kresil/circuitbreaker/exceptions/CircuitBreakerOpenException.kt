package kresil.circuitbreaker.exceptions

import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.state.CircuitBreakerState

/**
 * Signals that the [CircuitBreaker] is broken, either because it is in the `OPEN` state, or because it is in the `HALF_OPEN` state and the number of permitted calls has been exceeded.
 */
class CircuitBreakerOpenException(message: String) : RuntimeException(message) {
    constructor(state: CircuitBreakerState) : this("Circuit breaker is in state $state, and does not permit further calls.")
}
