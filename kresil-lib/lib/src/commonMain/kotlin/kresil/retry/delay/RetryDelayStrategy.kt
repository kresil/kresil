package kresil.retry.delay

import kresil.core.delay.DelayStrategy

/**
 * Represents the delay strategy that determines next delay duration before retrying an operation,
 * based on the current attempt and additional context.
 * @see [DelayStrategy]
 */
typealias RetryDelayStrategy = DelayStrategy<RetryDelayStrategyContext>
