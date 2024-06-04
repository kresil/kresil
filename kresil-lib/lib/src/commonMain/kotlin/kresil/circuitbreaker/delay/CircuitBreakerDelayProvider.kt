package kresil.circuitbreaker.delay

import kresil.circuitbreaker.state.CircuitBreakerState.HalfOpen
import kresil.circuitbreaker.state.CircuitBreakerState.Open
import kresil.core.delay.DelayProvider

/**
 * Defines the delay strategy for delaying the transition from [Open] to [HalfOpen], with the ability to use a custom delay provider with optional state.
 *
 * See [DelayProvider] for more information.
 * @see [CircuitBreakerDelayStrategy]
 */
fun interface CircuitBreakerDelayProvider : DelayProvider<Unit>
