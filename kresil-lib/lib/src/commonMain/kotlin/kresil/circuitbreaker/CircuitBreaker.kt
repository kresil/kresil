package kresil.circuitbreaker

import kotlinx.atomicfu.atomic
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.state.CircuitBreakerState

class CircuitBreaker(
    config: CircuitBreakerConfig
) {

    private val _state = atomic(CircuitBreakerState.CLOSED)
    val state: CircuitBreakerState by _state

}
