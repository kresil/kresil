package kresil.circuitbreaker.state.slidingwindow

import kresil.circuitbreaker.config.CircuitBreakerConfig

/**
 * Represents all the types of [SlidingWindow]s that can be configured in [CircuitBreakerConfig].
 */
enum class SlidingWindowType {

    /**
     * A sliding window that counts the number of failures (or successes) that occurred in the last N calls.
     */
    COUNT_BASED,

    /**
     * A sliding window that counts the number of failures (or successes) that occurred in the last N seconds.
     */
    TIME_BASED,
}
