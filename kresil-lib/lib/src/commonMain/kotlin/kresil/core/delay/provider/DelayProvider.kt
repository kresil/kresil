package kresil.core.delay.provider

import kotlin.time.Duration
import kresil.core.delay.DelayStrategy

/**
 * A delay provider executes the actual waiting period, in contrast to a [DelayStrategy],
 * which only determines the delay duration.
 * This provider can be used to implement custom delay logic with optional state between attempts.
 *
 * **Stateless Example**:
 * ```
 * val statelessDelayProvider = DelayProvider { attempt ->
 *    val nextDuration = when {
 *       attempt % 2 == 0 -> 1.seconds
 *       else -> 2.seconds
 *    }
 *    externalDelay(nextDuration) // your custom delay provider
 *    Duration.ZERO // to skip the default delay provider
 * }
 * ```
 *
 * **Stateful Example**:
 * ```
 * val statefulDelayProvider = object : DelayProvider<TContext> {
 *    var delayProviderRetryCounter = 0
 *       private set
 *    override suspend fun delay(attempt: Int): Duration {
 *       val nextDuration = when {
 *          ++delayProviderRetryCounter % 2 == 0 -> 1.seconds
 *          else -> 2.seconds
 *       }
 *       externalDelay(nextDuration) // your custom delay provider
 *       return Duration.ZERO // to skip the default delay provider
 *    }
 * }
 * ```
 * **Note**:
 * A stateless custom delay provider
 * that does not use an external delay will have the same behaviour as a [DelayStrategy].
 * As such, it is recommended to use it instead.
 *
 * By default, [kotlinx.coroutines.delay] is used as the delay provider.
 */
fun interface DelayProvider {

    /**
     * Determines the delay between retries.
     * If the return value is `Duration.ZERO`, the delay is considered to be defined externally and the default delay provider is skipped.
     * @param attempt the current retry attempt. Starts at **1**.
     */
    suspend fun delay(attempt: Int): Duration
}
