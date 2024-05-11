package kresil.retry.delay

import kotlin.time.Duration

/**
 * Provider for the delay between retries.
 * Defines the delay strategies for retrying an operation with the ability to use custom delay providers and optional state.
 *
 * **Stateless Example**:
 * ```
 * val statelessDelayProvider = RetryDelayProvider { attempt, lastThrowable ->
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
 * val statefulDelayProvider = object : RetryDelayProvider {
 *    var delayProviderRetryCounter = 0
 *       private set
 *    override suspend fun delay(attempt: Int, lastThrowable: Throwable?): Duration {
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
 * that does not use an external delay will have the same behaviour as a [RetryDelayStrategy].
 * As such, it is recommended to use it instead.
 *
 * By default, [kotlinx.coroutines.delay] is used as the delay provider.
 */
fun interface RetryDelayProvider {

    /**
     * Determines the delay between retries. This method is called for each retry attempt.
     * If the return value is `Duration.ZERO`, the delay is considered to be defined externally and the default delay provider is skipped.
     * @param attempt the current retry attempt. Starts at **1**.
     * @param lastThrowable the last throwable caught, if any.
     */
    suspend fun delay(attempt: Int, lastThrowable: Throwable?): Duration
}
