package kresil.retry.delay

import kresil.core.delay.DelayProvider

/**
 * Defines the delay strategy for retrying an operation with the ability to use a custom delay provider with optional state.
 *
 * See [DelayProvider] for more information.
 * @see [RetryDelayStrategy]
 */
fun interface RetryDelayProvider : DelayProvider<RetryDelayStrategyContext>
