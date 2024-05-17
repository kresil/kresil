package kresil.circuitbreaker.exceptions

import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.state.CircuitBreakerState.*

/**
 * Signals that the [CircuitBreaker] is broken (opened),
 * either because it was in the [CLOSED] state and the failure rate exceeded the threshold;
 * or because it was in the [HALF_OPEN] state and the number of permitted calls was exceeded.
 */
class CircuitBreakerOpenException(
    message: String = "Circuit breaker is in OPEN state, and does not permit further calls."
) : RuntimeException(message)
