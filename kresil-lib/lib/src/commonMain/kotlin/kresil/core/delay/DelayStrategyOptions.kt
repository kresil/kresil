package kresil.core.delay

import kresil.core.delay.DelayStrategyOptions.constant
import kresil.core.delay.DelayStrategyOptions.customProvider
import kresil.core.delay.DelayStrategyOptions.exponential
import kresil.core.delay.DelayStrategyOptions.noDelay
import kotlin.math.pow
import kotlin.time.Duration

/**
 * Represents a delay strategy used to determine the delay duration between attempts, where:
 * - `attempt` is the current attempt. Starts at **1**.
 * - `context` is the context to use for the delay strategy.
 *
 * If the return value is `Duration.ZERO`,
 * the delay is considered to be **defined externally** or not needed
 * (**no delay**); as such, the **default delay provider is skipped**.
 */
typealias DelayStrategy<TContext> = suspend (attempt: Int, context: TContext) -> Duration

/**
 * Represents all the options available for configuring a [DelayStrategy].
 * @see [noDelay]
 * @see [constant]
 * @see [exponential]
 * @see [customProvider]
 */
internal object DelayStrategyOptions {

    /**
     * A delay strategy that has no delay between retries.
     * Retries are immediate and do not use any custom delay provider.
     */
    fun <TContext> noDelay(): DelayStrategy<TContext> = { _, _ -> Duration.ZERO }

    /**
     * A delay strategy that uses a constant delay duration.
     * The delay between retries is the same for each attempt.
     * @param delay The constant delay duration to use.
     */
    fun <TContext> constant(delay: Duration): DelayStrategy<TContext> = { _, _ -> delay }

    /**
     * A delay strategy that uses a linear delay duration.
     * The delay between retries is calculated using the formula:
     * `initialDelay * attempt`, where `attempt` is the current attempt.
     * @param initialDelay The initial delay before the first attempt.
     * @param maxDelay The maximum delay between retries. Used as a safety net to prevent infinite delays.
     */
    fun <TContext> linear(initialDelay: Duration, maxDelay: Duration): DelayStrategy<TContext> =
        exponential(initialDelay, 1.0, maxDelay)

    /**
     * A delay strategy that uses an exponential delay duration.
     * The delay between retries is calculated using the formula:
     * `initialDelay * multiplier^attempt`, where `attempt` is the current attempt.
     * @param initialDelay The initial delay before the first attempt.
     * @param multiplier The multiplier to increase the delay between retries.
     * @param maxDelay The maximum delay between retries. Used as a safety net to prevent infinite delays.
     */
    fun <TContext> exponential(
        initialDelay: Duration,
        multiplier: Double,
        maxDelay: Duration,
    ): DelayStrategy<TContext> =
        { attempt, _ ->
            val nextDurationMillis = initialDelay * multiplier.pow(attempt)
            nextDurationMillis.coerceAtMost(maxDelay)
        }

    /**
     * A delay strategy that uses a custom delay provider, which determines, not only the delay between retries,
     * but also executes the actual waiting period.
     * @param provider The custom delay provider to use.
     * @see [DelayProvider]
     */
    fun <TContext> customProvider(provider: DelayProvider<TContext>): DelayStrategy<TContext> = { attempt, context ->
        provider.delay(attempt, context)
    }
}
