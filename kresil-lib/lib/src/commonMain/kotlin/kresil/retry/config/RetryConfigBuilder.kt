package kresil.retry.config

import kresil.core.builders.ConfigBuilder
import kresil.core.callbacks.ExceptionHandler
import kresil.core.callbacks.OnExceptionPredicate
import kresil.core.callbacks.OnResultPredicate
import kresil.core.delay.strategy.DelayStrategy
import kresil.core.delay.strategy.DelayStrategyOptions
import kresil.retry.delay.RetryCtxDelayProvider
import kresil.retry.delay.RetryDelayStrategy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Builder for configuring a [RetryConfig] instance.
 * Use [retryConfig] to create one.
 */
class RetryConfigBuilder(
    override val baseConfig: RetryConfig = defaultRetryConfig,
) : ConfigBuilder<RetryConfig> {

    // delay strategy options
    private val retryDelayStrategyOptions = DelayStrategyOptions

    // state
    private var exceptionHandler: ExceptionHandler = baseConfig.exceptionHandler
    private var delayStrategy: RetryDelayStrategy = baseConfig.delayStrategy
    private var retryPredicate: OnExceptionPredicate = baseConfig.retryPredicate
    private var retryOnResultPredicate: OnResultPredicate = baseConfig.retryOnResultPredicate

    /**
     * The maximum number of attempts **(including the initial call as the first attempt)**.
     */
    var maxAttempts: Int = baseConfig.maxAttempts

    /**
     * Configures the retry predicate.
     * The predicate is used to determine if, based on the caught throwable, the operation should be retried.
     * @param predicate the predicate to use.
     * @see retryOnResult
     */
    fun retryIf(predicate: OnExceptionPredicate) {
        retryPredicate = predicate
    }

    /**
     * Configures the retry on result predicate.
     * The predicate is used to determine if, based on the result of the operation, the operation should be retried.
     * @param predicate the predicate to use.
     * @see retryIf
     */
    fun retryOnResult(predicate: OnResultPredicate) {
        retryOnResultPredicate = predicate
    }

    /**
     * Configures the retry delay strategy to have no delay between retries (i.e., retries are immediate and do not use
     * any custom delay provider.
     * @see [constantDelay]
     * @see [linearDelay]
     * @see [exponentialDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     */
    fun noDelay() {
        delayStrategy = retryDelayStrategyOptions
            .noDelay()
            .toRetryDelayStrategy()
    }

    /**
     * Configures the retry delay strategy to use a constant delay.
     * The delay between retries is calculated using the formula:
     * - `delay + jitter`
     *
     * Example:
     * ```
     * constant(500.milliseconds)
     * // Delay between attempts will be 500ms
     * constant(500.milliseconds, 0.1)
     * // Delay between attempts will be something like:
     * // [495ms, 513ms, 502ms, 507ms, 499ms, ...]
     * ```
     * **Note**:
     * - Because the jitter calculation is based on the newly calculated delay, the new delay could be less than the previous value.
     * @param duration the constant delay between retries.
     * @param randomizationFactor the randomization factor to add randomness to the calculated delay (e.g., 0.1 for +/-10%).
     * @see [noDelay]
     * @see [linearDelay]
     * @see [exponentialDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     */
    fun constantDelay(
        duration: Duration,
        randomizationFactor: Double = 0.0,
    ) {
        delayStrategy = retryDelayStrategyOptions
            .constant(duration, randomizationFactor)
            .toRetryDelayStrategy()
    }

    /**
     * Configures the retry delay strategy to use the linear backoff algorithm.
     * The delay between retries is calculated using the formula:
     * - `initialDelay + (initialDelay * (attempt - 1) * multiplier) + jitter`,
     * where `attempt` is the current delay attempt which starts at **1**.
     *
     * Example:
     * ```
     * linearDelay(500.milliseconds, 1.0, 1.minutes)
     * // Delay between transitions will be as follows:
     * // [500ms, 1s, 1.5s, 2s, 2.5s, 3s, 3.5s, 4s, 4s, 4s, ...]
     * linearDelay(500.milliseconds, 1.0, 1.minutes, 0.1)
     * // Delay between transitions will be something like:
     * // [450ms, 1.1s, 1.4s, 2.2s, 2.3s, 3.1s, 3.4s, 4s, 4s, 4s, ...]
     * ```
     * **Note**:
     * - Because the jitter calculation is based on the newly calculated delay, the new delay could be less than the previous value.
     * @param initialDelay the initial delay before the first retry.
     * @param multiplier the multiplier to increase the delay between retries.
     * @param maxDelay the maximum delay between retries. Used as a safety net to prevent infinite delays.
     * @param randomizationFactor the randomization factor to add randomness to the calculated delay (e.g., 0.1 for +/-10%).
     * @see [constantDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     * @see [noDelay]
     */
    fun linearDelay(
        initialDelay: Duration = 500L.milliseconds,
        multiplier: Double = 1.0,
        maxDelay: Duration = 1.minutes,
        randomizationFactor: Double = 0.0,
    ) {
        delayStrategy = retryDelayStrategyOptions
            .linear(initialDelay, multiplier, maxDelay, randomizationFactor)
            .toRetryDelayStrategy()
    }

    /**
     * Configures the retry delay strategy to use the exponential backoff algorithm.
     * The delay between retries is calculated using the formula:
     * The algorithm is based on the formula:
     * - `(initialDelay * multiplier^(attempt - 1)) + jitter`,
     * where `attempt` is the current delay attempt which starts at **1**.
     *
     * Example:
     * ```
     * exponential(500.milliseconds, 2.0, 1.minutes)
     * // Delay between transitions will be as follows:
     * // [500ms, 1s, 2s, 4s, 8s, 16s, 32s, 1m, 1m, 1m, ...]
     * exponential(500.milliseconds, 2.0, 1.minutes, 0.1)
     * // Delay between transitions will be something like:
     * // [450ms, 1.1s, 1.4s, 2.2s, 2.3s, 3.1s, 3.4s, 4s, 4s, 4s, ...]
     * ```
     * **Note**:
     * - Because the jitter calculation is based on the newly calculated delay, the new delay could be less than the previous value.
     * @param initialDelay the initial delay before the first retry.
     * @param multiplier the multiplier to increase the delay between retries.
     * @param maxDelay the maximum delay between retries. Used as a safety net to prevent infinite delays.
     * @param randomizationFactor the randomization factor to add randomness to the calculated delay (e.g., 0.1 for +/-10%).
     * @see [noDelay]
     * @see [constantDelay]
     * @see [linearDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     */
    fun exponentialDelay(
        initialDelay: Duration = 500L.milliseconds,
        multiplier: Double = 2.0,
        maxDelay: Duration = 1.minutes,
        randomizationFactor: Double = 0.0,
    ) {
        delayStrategy = retryDelayStrategyOptions
            .exponential(initialDelay, maxDelay, multiplier, randomizationFactor)
            .toRetryDelayStrategy()
    }

    /**
     * Configures the retry delay strategy to use a custom delay strategy.
     *
     * Example:
     * ```
     * customDelay { attempt, context ->
     *      if (attempt % 2 == 0) 1.seconds
     *      // additional state can be used from the context
     *      else 3.seconds
     * }
     * ```
     * @param delayStrategy the custom delay strategy to use.
     * @see [noDelay]
     * @see [constantDelay]
     * @see [linearDelay]
     * @see [exponentialDelay]
     * @see [customDelayProvider]
     **/
    fun customDelay(delayStrategy: RetryDelayStrategy) {
        this.delayStrategy = delayStrategy
    }

    /**
     * Configures the retry delay strategy to use a custom delay provider.
     * In contrast to [customDelay], this method enables caller control over the delay provider (which is the
     * [kotlinx.coroutines.delay] by default) and optional additional state between retries.
     * See [RetryCtxDelayProvider] for more information and examples of usage.
     * @param delayProvider the custom delay provider to use.
     * @see [noDelay]
     * @see [constantDelay]
     * @see [linearDelay]
     * @see [exponentialDelay]
     * @see [customDelay]
     */
    fun customDelayProvider(delayProvider: RetryCtxDelayProvider) {
        delayStrategy = retryDelayStrategyOptions.customProvider(delayProvider)
    }

    /**
     * Configures the exception handler to use when retries are exhausted.
     * By default, the exception, if any, is thrown.
     * For example, if maximum attempts are reached and the exception handler is not set, the exception will be thrown.
     * Use this method to handle exceptions that occur during the retry operation in a custom way (e.g., logging specific exceptions).
     * @param handler the exception handler to use.
     */
    fun exceptionHandler(handler: ExceptionHandler) {
        exceptionHandler = handler
    }

    /**
     * Disables the exception handler.
     * By default, the exception, if any, is thrown.
     * @see [exceptionHandler]
     */
    fun disableExceptionHandler() {
        exceptionHandler = { it }
    }

    /**
     * Builds the [RetryConfig] instance with the configured properties.
     */
    override fun build() = RetryConfig(
        maxAttempts,
        retryPredicate,
        retryOnResultPredicate,
        delayStrategy,
        exceptionHandler
    )
}

/**
 * The default retry configuration.
 */
private val defaultRetryConfig = RetryConfig(
    maxAttempts = 3,
    retryPredicate = { true },
    retryOnResultPredicate = { false },
    delayStrategy = DelayStrategyOptions.exponential(
        initialDelay = 500.milliseconds,
        multiplier = 2.0,
        maxDelay = 1.minutes,
        randomizationFactor = 0.0
    ).toRetryDelayStrategy(),
    exceptionHandler = { throw it }
)

/**
 * Converts a [DelayStrategy] into a [RetryDelayStrategy] by ignoring the context.
 */
private fun DelayStrategy.toRetryDelayStrategy(): RetryDelayStrategy = { attempt, _ ->
    this(attempt)
}
