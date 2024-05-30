package kresil.retry.delay

import kresil.core.delay.DelayStrategy

/**
 * Represents the delay strategy to use for retrying an operation.
 * @see [DelayStrategy]
 */
typealias RetryDelayStrategy = DelayStrategy<RetryDelayStrategyContext>
