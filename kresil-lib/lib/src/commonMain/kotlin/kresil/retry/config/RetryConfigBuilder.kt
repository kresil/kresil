package kresil.retry.config

import kresil.core.builders.ConfigBuilder
import kresil.retry.delay.RetryDelayProvider
import kresil.retry.delay.RetryDelayStrategy
import kresil.retry.delay.RetryDelayStrategyOptions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Predicate to determine if the operation should be retried based on the caught throwable.
 */
typealias RetryPredicate = (Throwable) -> Boolean

/**
 * Predicate to determine if the operation should be retried based on the result of the operation.
 */
typealias RetryOnResultPredicate = (result: Any?) -> Boolean

/**
 * Callback to execute before the operation is called.
 * Receives the current retry attempt as an argument.
 */
typealias BeforeOperationCallback = (attempt: Int) -> Unit

/**
 * Callback to handle the retried operation that failed.
 * Can be used to stop error propagation of the error or add additional logging.
 */
typealias ExceptionHandler = (throwable: Throwable) -> Unit

/**
 * Builder for configuring a [RetryConfig] instance.
 * Use [retryConfig] to create one.
 */
class RetryConfigBuilder(
    override val baseConfig: RetryConfig = defaultRetryConfig
) : ConfigBuilder<RetryConfig> {

    // delay helper
    private val retryDelayStrategyOptions = RetryDelayStrategyOptions

    // state
    private var exceptionHandler: ExceptionHandler = baseConfig.exceptionHandler
    private var delayStrategy: RetryDelayStrategy = baseConfig.delayStrategy
    private var beforeOperationCallback: BeforeOperationCallback = baseConfig.beforeOperationCallback
    private var retryPredicate: RetryPredicate = baseConfig.retryPredicate
    private var retryOnResultPredicate: RetryOnResultPredicate = baseConfig.retryOnResultPredicate

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
    fun retryIf(predicate: RetryPredicate) {
        retryPredicate = predicate
    }

    /**
     * Configures the callback to execute before the operation is called.
     * Receives the current retry attempt as an argument.
     * @param callback the callback to execute.
     */
    fun beforeOperCallback(callback: BeforeOperationCallback) {
        beforeOperationCallback = callback
    }

    /**
     * Configures the retry on result predicate.
     * The predicate is used to determine if, based on the result of the operation, the operation should be retried.
     * @param predicate the predicate to use.
     * @see retryIf
     */
    fun retryOnResult(predicate: RetryOnResultPredicate) {
        retryOnResultPredicate = predicate
    }

    /**
     * Configures the retry delay strategy to use a constant delay (i.e., the same delay between retries).
     * @param duration the constant delay between retries.
     * @throws IllegalArgumentException if the duration is less than or equal to 0.
     * @see [exponentialDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     * @see [noDelay]
     */
    @Throws(IllegalArgumentException::class)
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
     * @throws IllegalArgumentException if the initial delay is less than or equal to 0 or the multiplier is less than or equal to 1.0.
     * @see [constantDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     * @see [noDelay]
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
     * @see [customDelayProvider]
     * @see [constantDelay]
     * @see [exponentialDelay]
     * @see [noDelay]
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
     * @see [exponentialDelay]
     * @see [constantDelay]
     * @see [customDelay]
     * @see [noDelay]
     */
    fun customDelayProvider(delayProvider: RetryDelayProvider) {
        delayStrategy = retryDelayStrategyOptions.customProvider(delayProvider)
    }

    /**
     * Configures the retry delay strategy to have no delay between retries (i.e., retries are immediate and do not use
     * any custom delay provider.
     * @see [constantDelay]
     * @see [exponentialDelay]
     * @see [customDelay]
     * @see [customDelayProvider]
     */
    fun noDelay() {
        delayStrategy = retryDelayStrategyOptions.noDelay()
    }

    /**
     * Configures the callback to handle the retried operation that failed.
     * The default behavior is to propagate the error.
     * @param callback the callback to execute.
     */
    fun exceptionHandler(callback: ExceptionHandler) {
        exceptionHandler = callback
    }

    /**
     * Builds the [RetryConfig] instance with the configured properties.
     */
    override fun build() = RetryConfig(
        maxAttempts,
        retryPredicate,
        retryOnResultPredicate,
        delayStrategy,
        beforeOperationCallback,
        exceptionHandler
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
    beforeOperationCallback = { },
    exceptionHandler = { throw it } // propagate the error by default
)
