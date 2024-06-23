package kresil.retry.delay

import kresil.core.delay.strategy.CtxDelayStrategy

/**
 * Represents the delay strategy that determines next delay duration before retrying an operation,
 * based on the current attempt and additional context.
 * @see [CtxDelayStrategy]
 */
typealias RetryDelayStrategy = CtxDelayStrategy<RetryDelayStrategyContext>
