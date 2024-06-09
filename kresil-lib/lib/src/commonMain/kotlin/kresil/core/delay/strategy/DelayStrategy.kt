package kresil.core.delay.strategy

import kresil.core.delay.provider.CtxDelayProvider
import kotlin.time.Duration

/**
 * Represents a delay strategy used to determine the delay duration between attempts, where:
 * - `attempt` is the current attempt. Starts at **1**.
 *
 * If the return value is `Duration.ZERO`,
 * the delay is considered to be **defined externally** or not needed
 * (**no delay**); as such, the **default delay provider is skipped**.
 * See [CtxDelayStrategy] for a context-aware version.
 * @see [DelayStrategyOptions]
 * @see [CtxDelayProvider]
 */
typealias DelayStrategy = suspend (attempt: Int) -> Duration

/**
 * Represents a delay strategy with context used to determine the delay duration between attempts, where:
 * - `attempt` is the current attempt. Starts at **1**.
 * - `context` is the additional context provided to the strategy (e.g., the last throwable caught).
 *
 * If the return value is `Duration.ZERO`,
 * the delay is considered to be **defined externally** or not needed
 * (**no delay**); as such, the **default delay provider is skipped**.
 * See [DelayStrategy] for a context-agnostic version.
 * @see [DelayStrategyOptions]
 * @see [CtxDelayProvider]
 */
typealias CtxDelayStrategy<TContext> = suspend (attempt: Int, context: TContext) -> Duration
