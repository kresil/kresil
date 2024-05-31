package kresil.circuitbreaker.slidingwindow

import kresil.circuitbreaker.config.CircuitBreakerConfig

/**
 * Represents a sliding window that can be configured in [CircuitBreakerConfig].
 * @param size The size of the sliding window.
 * @param minimumThroughput The minimum number of calls that must be made before the failure rate can be calculated.
 * @param type The type of the sliding window.
 */
data class SlidingWindow(
    val size: Int,
    val minimumThroughput: Int,
    val type: SlidingWindowType,
)
