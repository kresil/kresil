package kresil.core.delay

import kresil.core.delay.DelayStrategyOptions.constant
import kresil.core.delay.DelayStrategyOptions.customProvider
import kresil.core.delay.DelayStrategyOptions.exponential
import kresil.core.delay.DelayStrategyOptions.noDelay
import kotlin.math.pow
import kotlin.time.Duration

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
 * Represents all the options available for configuring a delay strategy.
 * @see [noDelay]
 * @see [constant]
 * @see [exponential]
 * @see [customProvider]
 */
internal object DelayStrategyOptions {

    // TODO: add checks for parameters

    /**
     * A delay strategy that has no delay between attempts.
     * Attempts are immediate and do not use any custom delay provider.
     */
    fun noDelay(): DelayStrategy = { _ -> Duration.ZERO }

    /**
     * A delay strategy that uses a constant delay duration.
     * The delay between attempts is the same for each attempt.
     * @param delay The constant delay duration to use.
     */
    fun constant(delay: Duration): DelayStrategy = { _ -> delay }

    /**
     * A delay strategy that uses a linear function to calculate the next delay duration.
     * The delay between attempts is calculated using the formula:
     * `initialDelay * attempt`, where `attempt` is the current attempt.
     * @param initialDelay The initial delay before the first attempt.
     * @param maxDelay The maximum delay between attempts. Used as a safety net to prevent infinite delays.
     */
    fun linear(initialDelay: Duration, maxDelay: Duration): DelayStrategy =
        exponential(initialDelay, 1.0, maxDelay)

    /**
     * A delay strategy that uses an exponential function to calculate the next delay duration.
     * The delay between attempts is calculated using the formula:
     * `initialDelay * multiplier^attempt`, where `attempt` is the current attempt.
     * @param initialDelay The initial delay before the first attempt.
     * @param multiplier The multiplier to increase the delay between attempts.
     * @param maxDelay The maximum delay between attempts. Used as a safety net to prevent infinite delays.
     */
    fun exponential(
        initialDelay: Duration,
        multiplier: Double,
        maxDelay: Duration,
    ): DelayStrategy =
        { attempt ->
            val nextDurationMillis = initialDelay * multiplier.pow(attempt)
            nextDurationMillis.coerceAtMost(maxDelay)
        }

    /**
     * A delay strategy that uses a custom delay provider, which extends the behaviour of a [DelayStrategy],
     * by allowing external control of the delay execution with optional state.
     */
    fun customProvider(provider: DelayProvider): DelayStrategy = { attempt ->
        provider.delay(attempt)
    }

    /**
     * A delay strategy that uses a custom delay provider,
     * which extends the behaviour of a [CtxDelayStrategy], by allowing external control of the delay execution with optional state.
     * @param provider The custom delay provider to use.
     * @see [CtxDelayProvider]
     */
    fun <TContext> customProvider(provider: CtxDelayProvider<TContext>): CtxDelayStrategy<TContext> =
        { attempt, context ->
            provider.delay(attempt, context)
        }

    /**
     * Converts a [DelayStrategy] into a [CtxDelayStrategy] using [Unit] as the context.
     * This is useful when no additional context is needed, allowing the caller to avoid providing a [Unit] context.
     * It maintains compatibility with the more general [CtxDelayStrategy] type.
     * @see [toEmptyCtxDelayStrategy]
     */
    fun DelayStrategy.toEmptyCtxDelayStrategy(): CtxDelayStrategy<Unit> = { attempt, _ ->
        this(attempt)
    }

}
