package kresil.retry.delay

import kresil.core.delay.DelayProvider

/**
 * Provider for the delay between retries.
 * Defines the delay strategy for retrying an operation with the ability to use custom delay providers and optional state.
 *
 * See [DelayProvider] for more information.
 */
fun interface RetryDelayProvider : DelayProvider<RetryDelayStrategyContext>
