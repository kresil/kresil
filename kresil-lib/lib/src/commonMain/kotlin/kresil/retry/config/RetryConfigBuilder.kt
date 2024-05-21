package kresil.retry.config

import kresil.core.builders.ConfigBuilder
import kresil.core.callbacks.ResultMapper
import kresil.core.callbacks.OnExceptionPredicate
import kresil.core.callbacks.OnResultPredicate
import kresil.retry.delay.RetryDelayProvider
import kresil.retry.delay.RetryDelayStrategy
import kresil.retry.delay.RetryDelayStrategyOptions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Builder for configuring a [RetryConfig] instance.
 * Use [retryConfig] to create one.
 */
class RetryConfigBuilder(
    override val baseConfig: RetryConfig = defaultRetryConfig
) : ConfigBuilder<RetryConfig> {

    // delay strategy options
    private val retryDelayStrategyOptions = RetryDelayStrategyOptions

    // state
    private var resultMapper: ResultMapper = baseConfig.resultMapper
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
        delayStrategy = retryDelayStrategyOptions.noDelay()
    }

    /**
     * Configures the retry delay strategy to use a constant delay (i.e., the same delay between retries).
     * @param duration the constant delay between retries.
     * @throws IllegalArgumentException if the duration is less than or equal to 0.
     * @see [noDelay]
     * @see [linearDelay]
     * @see [exponentialDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     */
    @Throws(IllegalArgumentException::class)
    fun constantDelay(duration: Duration) {
        requirePositiveDuration(duration, "Delay")
        delayStrategy = { _, _ -> duration }
    }

    /**
     * Configures the retry delay strategy to use a linear delay.
     * The delay between retries is calculated using the formula:
     *
     * `initialDelay * attempt`, where `attempt` is the current retry attempt.
     *
     * Example:
     * ```
     * linearDelay(500.milliseconds, 4.seconds)
     * // Delay between retries will be as follows:
     * // [500ms, 1s, 1.5s, 2s, 2.5s, 3s, 3.5s, 4s, 4s, 4s, ...]
     * ```
     *
     * **Note:** The delay is capped at the `maxDelay` value.
     * @param initialDelay the initial delay before the first retry.
     * @param maxDelay the maximum delay between retries. Used as a safety net to prevent infinite delays.
     * @throws IllegalArgumentException if the initial delay is less than or equal to 0.
     * @see [constantDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     * @see [noDelay]
     */
    @Throws(IllegalArgumentException::class)
    fun linearDelay(
        initialDelay: Duration = 500L.milliseconds,
        maxDelay: Duration = 1.minutes
    ) {
        requirePositiveDuration(initialDelay, "Initial delay")
        require(initialDelay < maxDelay) { "Max delay must be greater than initial delay" }
        delayStrategy = retryDelayStrategyOptions.linear(initialDelay, maxDelay)
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
     * @throws IllegalArgumentException if the initial delay is less than or equal to 0 or the multiplier is less than or equal to 1.0.
     * @see [noDelay]
     * @see [constantDelay]
     * @see [linearDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     */
    @Throws(IllegalArgumentException::class)
    fun exponentialDelay(
        initialDelay: Duration = 500L.milliseconds,
        multiplier: Double = 2.0, // not using constant to be readable for the user
        maxDelay: Duration = 1.minutes,
    ) {
        requirePositiveDuration(initialDelay, "Initial delay")
        require(multiplier > 1.0) { "Multiplier must be greater than 1" }
        require(initialDelay < maxDelay) { "Max delay must be greater than initial delay" }
        delayStrategy = retryDelayStrategyOptions.exponential(initialDelay, multiplier, maxDelay)
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
     * See [RetryDelayProvider] for more information and examples of usage.
     * @param delayProvider the custom delay provider to use.
     * @see [noDelay]
     * @see [constantDelay]
     * @see [linearDelay]
     * @see [exponentialDelay]
     * @see [customDelay]
     */
    fun customDelayProvider(delayProvider: RetryDelayProvider) {
        delayStrategy = retryDelayStrategyOptions.customProvider(delayProvider)
    }

    /**
     * Configures the mapper to use when retry is finished.
     *
     * The mapper can be used, for example, to:
     * - map the result or the exception to a specific type;
     * - throw the caught exception;
     * - log the exception and not throw it;
     * - return a default value when an exception occurs;
     * - etc.
     * @param mapper the mapper to use.
     */
    fun resultMapper(mapper: ResultMapper) {
        resultMapper = mapper
    }

    /**
     * Disables the exception handler mechanism.
     * The default behaviour is to throw the exception when it occurs.
     * This is a subtype of the [resultMapper] method,
     * used when no mapping is needed and the eventuality of an exception
     * should not be thrown.
     */
    fun disableExceptionHandler() {
        resultMapper = { result, _ -> result }
    }

    /**
     * Builds the [RetryConfig] instance with the configured properties.
     */
    override fun build() = RetryConfig(
        maxAttempts,
        retryPredicate,
        retryOnResultPredicate,
        delayStrategy,
        resultMapper
    )

    /**
     * Validates that the duration is in fact a positive duration.
     * @param duration the duration to validate.
     * @param qualifier the qualifier to use in the exception message.
     * @throws IllegalArgumentException if the duration is less than or equal to 0
     */
    @Throws(IllegalArgumentException::class)
    private fun requirePositiveDuration(duration: Duration, qualifier: String) {
        require(duration > Duration.ZERO) { "$qualifier duration must be greater than 0" }
    }
}

/**
 * The default retry configuration.
 */
private val defaultRetryConfig = RetryConfig(
    maxAttempts = 3,
    retryPredicate = { true },
    retryOnResultPredicate = { false },
    delayStrategy = RetryDelayStrategyOptions.exponential(
        initialDelay = 500.milliseconds,
        multiplier = 2.0,
        maxDelay = 1.minutes
    ),
    resultMapper = { result: Any?, throwable: Throwable? ->
        throwable?.let { throw it } ?: result
    }
)
