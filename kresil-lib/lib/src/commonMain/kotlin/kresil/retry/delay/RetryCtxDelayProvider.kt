package kresil.retry.delay

import kresil.core.delay.CtxDelayProvider

/**
 * Defines the delay strategy for retrying an operation with the ability to use a custom delay provider with optional state.
 *
 * See [CtxDelayProvider] for more information.
 * @see [RetryDelayStrategy]
 */
fun interface RetryCtxDelayProvider : CtxDelayProvider<RetryDelayStrategyContext>
