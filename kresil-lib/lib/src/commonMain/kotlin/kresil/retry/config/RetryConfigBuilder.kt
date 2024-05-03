package kresil.retry.config

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kresil.retry.builders.retryConfig
import kresil.retry.delay.RetryDelayProvider
import kresil.retry.delay.RetryDelayStrategy

/**
 * Specifies the delay strategy to use for retrying an operation.
 * Presents the same behaviour as a [RetryDelayStrategy],
 * with the added ability to use a custom delay provider.
 * The strategy is used to determine the delay duration between retries, where:
 * - `attempt` is the current retry attempt. Starts at **1**.
 * - `lastThrowable` is the last throwable caught, if any.
 *
 * If the return value is `null`, the delay is considered to be **defined externally** and the **default delay provider is skipped**.
 */
typealias SuspendRetryDelayStrategy = suspend (attempt: Int, lastThrowable: Throwable?) -> Duration?

/**
 * Predicate to determine if the operation should be retried based on the caught throwable.
 */
typealias RetryPredicate = (Throwable) -> Boolean

/**
 * Predicate to determine if the operation should be retried based on the result of the operation.
 */
typealias RetryOnResultPredicate = (Any?) -> Boolean

/**
 * Builder for configuring a [RetryConfig] instance.
 * Use [retryConfig] to create a [RetryConfig] instance.
 */
class RetryConfigBuilder internal constructor() {

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
    }

    init {
        exponentialDelay()
        retryIf { true }
        retryOnResult { false }
    }

    private lateinit var delayStrategy: SuspendRetryDelayStrategy
    private lateinit var retryIf: RetryPredicate
    private lateinit var retryOnResultIf: RetryOnResultPredicate

    /**
     * The maximum number of attempts **(including the initial call as the first attempt)**.
     */
    var maxAttempts: Int = DEFAULT_MAX_ATTEMPTS

    /**
     * Configures the retry on throwable predicate.
     * The predicate is used to determine if, based on the caught throwable, the operation should be retried.
     * @param predicate the predicate to use.
     * @see retryOnResultIf
     */
    fun retryIf(predicate: RetryPredicate) {
        retryIf = predicate
    }

    /**
     * Configures the retry on result predicate.
     * The predicate is used to determine if, based on the result of the operation, the operation should be retried.
     * @param predicate the predicate to use.
     * @see retryIf
     */
    fun retryOnResult(predicate: RetryOnResultPredicate) {
        retryOnResultIf = predicate
    }

    /**
     * Configures the retry delay strategy to use a constant delay (i.e., the same delay between retries).
     * @param duration the constant delay between retries.
     * @throws IllegalArgumentException if the duration is less than or equal to 0.
     * @see [exponentialDelay]
     * @see [customDelay]
     * @see [noDelay]
     */
    fun constantDelay(duration: Duration) {
        requirePositiveDuration(duration, "Delay")
        delayStrategy = { _, _ -> duration }
    }

    /**
     * Configures the retry delay strategy to use the exponential backoff algorithm.
     * The delay between retries is calculated using the formula:
     *
     * `initialDelay * multiplier^attempt`, where `attempt` is the current retry attempt.
     *
     * Example:
     * ```
     * exponentialDelay(500.milliseconds, 2.0, 1.minutes)
     * // Delay between retries will be as follows:
     * // [500ms, 1s, 2s, 4s, 8s, 16s, 32s, 1m, 1m, 1m, ...]
     * ```
     *
     * **Note:** The delay is capped at the `maxDelay` value.
     * @param initialDelay the initial delay before the first retry.
     * @param multiplier the multiplier to increase the delay between retries.
     * @param maxDelay the maximum delay between retries. Used as a safety net to prevent infinite delays.
     * @throws IllegalArgumentException if the initial delay is less than or equal to 0, the multiplier is less than or equal to 1.
     * @see [constantDelay]
     * @see [customDelay]
     * @see [noDelay]
     */
    fun exponentialDelay(
        initialDelay: Duration = 500L.milliseconds,
        multiplier: Double = 2.0, // not using constant to be readable for the user
        maxDelay: Duration = 1.minutes,
    ) {
        requirePositiveDuration(initialDelay, "Initial delay")
        require(multiplier > 1.0) { "Multiplier must be greater than 1" }
        val initialDelayMillis = initialDelay.inWholeMilliseconds
        val maxDelayMillis = maxDelay.inWholeMilliseconds
        require(initialDelayMillis < maxDelayMillis) { "Max delay must be greater than initial delay" }
        delayStrategy = { attempt, _ ->
            val nextDurationMillis = initialDelayMillis * multiplier.pow(attempt)
            nextDurationMillis.milliseconds.coerceAtMost(maxDelayMillis.milliseconds)
        }
    }

    /**
     * Configures the retry delay strategy to use a custom delay strategy.
     *
     * Example:
     * ```
     * customDelay { attempt, lastThrowable ->
     *      attempt % 2 == 0 -> 1.seconds
     *      lastThrowable is WebServiceException -> 2.seconds
     *      else -> 3.seconds
     * }
     * ```
     * Where:
     * - `attempt` is the current retry attempt. Starts at **1**.
     * - `lastThrowable` is the last throwable caught.
     * @param delayStrategy the custom delay strategy to use.
     * @see [customDelayProvider]
     * @see [constantDelay]
     * @see [exponentialDelay]
     * @see [noDelay]
     **/
    fun customDelay(delayStrategy: RetryDelayStrategy) {
        this.delayStrategy = { attempt, lastThrowable ->
            delayStrategy(attempt, lastThrowable)
        }
    }

    /**
     * Configures the retry delay strategy to use a custom delay provider.
     * In contrast to [customDelay], this method enables caller control over the delay provider (which is the
     * [kotlinx.coroutines.delay] by default) and optional additional state between retries.
     * See [RetryDelayProvider] for more information and examples of usage.
     * @param delayProvider the custom delay provider to use.
     * @see [exponentialDelay]
     * @see [constantDelay]
     * @see [customDelay]
     * @see [noDelay]
     */
    fun customDelayProvider(delayProvider: RetryDelayProvider) {
        this.delayStrategy = { attempt, lastThrowable ->
            delayProvider.delay(attempt, lastThrowable)
        }
    }

    /**
     * Configures the retry delay strategy to have no delay between retries (i.e., retries are immediate and do not use
     * any sleep duration provider.
     * @see [constantDelay]
     * @see [exponentialDelay]
     * @see [customDelay]
     */
    fun noDelay() {
        delayStrategy = { _, _ -> Duration.ZERO }
    }

    /**
     * Builds the [RetryConfig] instance with the configured properties.
     */
    fun build() = RetryConfig(
        maxAttempts,
        retryIf,
        retryOnResultIf,
        delayStrategy
    )

    /**
     * Validates that the duration is in fact a positive duration.
     * @param duration the duration to validate.
     * @param qualifier the qualifier to use in the exception message.
     * @throws IllegalArgumentException if the duration is less than or equal to 0
     */
    private fun requirePositiveDuration(duration: Duration, qualifier: String) {
        require(duration > Duration.ZERO) { "$qualifier duration must be greater than 0" }
    }

}
