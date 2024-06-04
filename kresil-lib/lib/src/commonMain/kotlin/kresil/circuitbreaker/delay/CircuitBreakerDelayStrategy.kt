package kresil.circuitbreaker.delay

import kresil.core.delay.DelayStrategy
import kresil.circuitbreaker.state.CircuitBreakerState.*

/**
 * Represents the delay strategy that determines next delay duration in [Open] before switching to [HalfOpen],
 * based on the current transition attempt and additional context.
 * This strategy for complex delay strategies that can be used to further delay the transition
 * (e.g., to give the operation, and the underlying system, more time to recover after a failure in [HalfOpen]).
 * @see [DelayStrategy]
*/
typealias CircuitBreakerDelayStrategy = DelayStrategy<Unit>
