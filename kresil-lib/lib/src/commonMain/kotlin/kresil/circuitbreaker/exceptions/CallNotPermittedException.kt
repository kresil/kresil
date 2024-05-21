package kresil.circuitbreaker.exceptions

import kresil.circuitbreaker.state.CircuitBreakerState.CLOSED
import kresil.circuitbreaker.state.CircuitBreakerState.HALF_OPEN

/**
 * Signals that a call to a protected operation was not permitted by the circuit breaker
 * either because it was in the [CLOSED] state and the failure rate exceeded the threshold;
 * or because it was in the [HALF_OPEN] state and the number of permitted calls was exceeded.
 */
class CallNotPermittedException(
    message: String = "A call to a protected operation by a circuit breaker was not permitted."
) : IllegalStateException(message)
