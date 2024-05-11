package kresil.retry.delay

import kresil.retry.delay.RetryDelayStrategyOptions.constant
import kresil.retry.delay.RetryDelayStrategyOptions.customProvider
import kresil.retry.delay.RetryDelayStrategyOptions.exponential
import kresil.retry.delay.RetryDelayStrategyOptions.noDelay
import kotlin.math.pow
import kotlin.time.Duration

/**
 * Specifies the delay strategy to use for retrying an operation.
 * Presents the same behaviour as a [RetryDelayStrategy],
 * with the added ability to use a custom delay provider.
 * The strategy is used to determine the delay duration between retries, where:
 * - `attempt` is the current retry attempt. Starts at **1**.
 * - `lastThrowable` is the last throwable caught, if any.
 *
 * If the return value is `Duration.ZERO`,
 * the delay is considered to be **defined externally** or not needed
 * (**no delay**); as such, the **default delay provider is skipped**.
 */
typealias RetryDelayStrategy = suspend (attempt: Int, lastThrowable: Throwable?) -> Duration

/**
 * Represents all the options available for configuring a [RetryDelayStrategy].
 * @see [noDelay]
 * @see [constant]
 * @see [exponential]
 * @see [customProvider]
 */
internal object RetryDelayStrategyOptions {

    /**
     * A delay strategy that has no delay between retries.
     * Retries are immediate and do not use any custom delay provider.
     */
    fun noDelay(): RetryDelayStrategy = { _, _ -> Duration.ZERO }

    /**
     * A delay strategy that uses a constant delay duration.
     * The delay between retries is the same for each attempt.
     * @param delay The constant delay duration to use.
     */
    fun constant(delay: Duration): RetryDelayStrategy = { _, _ -> delay }

    /**
     * A delay strategy that uses a linear delay duration.
     * The delay between retries is calculated using the formula:
     * `initialDelay * attempt`, where `attempt` is the current retry attempt.
     * @param initialDelay The initial delay before the first retry.
     * @param maxDelay The maximum delay between retries. Used as a safety net to prevent infinite delays.
     */
    fun linear(initialDelay: Duration, maxDelay: Duration): RetryDelayStrategy =
        exponential(initialDelay, 1.0, maxDelay)

    /**
     * A delay strategy that uses an exponential delay duration.
     * The delay between retries is calculated using the formula:
     * `initialDelay * multiplier^attempt`, where `attempt` is the current retry attempt.
     * @param initialDelay The initial delay before the first retry.
     * @param multiplier The multiplier to increase the delay between retries.
     * @param maxDelay The maximum delay between retries. Used as a safety net to prevent infinite delays.
     */
    fun exponential(initialDelay: Duration, multiplier: Double, maxDelay: Duration): RetryDelayStrategy =
        { attempt, _ ->
            val nextDurationMillis = initialDelay * multiplier.pow(attempt)
            nextDurationMillis.coerceAtMost(maxDelay)
        }

    /**
     * A delay strategy that uses a custom delay provider.
     * The delay between retries is determined by the custom delay provider.
     * @param provider The custom delay provider to use.
     * @see [RetryDelayProvider]
     */
    fun customProvider(provider: RetryDelayProvider): RetryDelayStrategy = { attempt, lastThrowable ->
        provider.delay(attempt, lastThrowable)
    }
}
