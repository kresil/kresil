package kresil.ktor.client.plugins.retry.config

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kresil.core.builders.ConfigBuilder
import kresil.core.callbacks.OnExceptionPredicate
import kresil.retry.config.RetryConfigBuilder
import kresil.retry.delay.RetryCtxDelayProvider
import kresil.retry.delay.RetryDelayStrategy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kresil.ktor.client.plugins.retry.KresilRetryPlugin
import kresil.ktor.client.plugins.retry.exceptions.RetryOnCallException

/**
 * Predicate to determine if an HTTP call should be retried based on the request and response.
 */
internal typealias RetryOnCallPredicate = (HttpRequest, HttpResponse) -> Boolean

/**
 * Callback to modify the request between retries.
 */
internal typealias ModifyRequestOnRetry = (HttpRequestBuilder, attempt: Int) -> Unit

/**
 * Builder for configuring the [KresilRetryPlugin].
 */
class RetryPluginConfigBuilder(override val baseConfig: RetryPluginConfig) : ConfigBuilder<RetryPluginConfig> {

    private val retryConfigBuilder: RetryConfigBuilder = RetryConfigBuilder(baseConfig.retryConfig)
    private var retryPredicate: OnExceptionPredicate = baseConfig.retryConfig.retryPredicate
    private var retryOnCallPredicate: RetryOnCallPredicate = baseConfig.retryOnCallPredicate
    private var modifyRequest: ModifyRequestOnRetry = baseConfig.modifyRequestOnRetry

    /**
     * The maximum number of attempts **(including the initial call as the first attempt)**.
     */
    var maxAttempts: Int = baseConfig.retryConfig.maxAttempts
        set(value) {
            // connect value change with the retry config builder
            retryConfigBuilder.maxAttempts = value
            field = value
        }

    /**
     * Configures a predicate to determine if an HTTP call should be retried based on the respective request and response.
     * @see retryOnServerErrors
     * @see retryOnServerErrorsIfIdempotent
     * @see retryOnTimeout
     */
    fun retryOnCall(block: (request: HttpRequest, response: HttpResponse) -> Boolean) {
        retryOnCallPredicate = block
    }

    /**
     * Retries the HTTP call if the response status code is in the range: **500-599**.
     * @see [retryOnCall]
     * @see [retryOnServerErrorsIfIdempotent]
     * @see [retryOnTimeout]
     */
    fun retryOnServerErrors() {
        retryOnCall { _, response ->
            response.status.value in 500..599
        }
    }

    /**
     * Retries the HTTP call if the request method is idempotent
     * (i.e.,
     * the intended effect on the server of multiple identical requests with that method is the same as the effect for a single such request)
     * and the response status code is in the specified [range].
     * Idempotent methods supported by Ktor are: `GET`, `HEAD`, `OPTIONS`, `PUT`, `DELETE`.
     * @see [retryOnCall]
     * @see [retryOnServerErrors]
     * @see [retryOnTimeout]
     */
    fun retryOnServerErrorsIfIdempotent(range: IntRange = 500..599) {
        retryOnCall { request, response ->
            request.method in listOf(
                HttpMethod.Get, HttpMethod.Delete, HttpMethod.Head, HttpMethod.Options, HttpMethod.Put
            ) && response.status.value in range
        }
    }

    /**
     * Retries the HTTP call if the exception thrown is a timeout exception.
     * See [HttpTimeout] plugin for more information on possible timeout exceptions.
     * If this method is used, [HttpTimeout] plugin should be installed after this plugin.
     * @see [retryOnCall]
     * @see [retryOnServerErrors]
     * @see [retryOnServerErrorsIfIdempotent]
     */
    fun retryOnTimeout() {
        retryPredicate = { it.isTimeoutException() }
    }

    /**
     * Configures the retry predicate, used to determine if, based on the caught throwable, the underlying request should be retried.
     * @param predicate the predicate to use.
     */
    fun retryOnException(predicate: OnExceptionPredicate) {
        retryPredicate = predicate
    }

    /**
     * Modifies the request between retries, before it is sent.
     * The block receives the [HttpRequestBuilder] and the current retry attempt as arguments.
     */
    fun modifyRequestOnRetry(block: (builder: HttpRequestBuilder, attempt: Int) -> Unit) {
        modifyRequest = block
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
        retryConfigBuilder.noDelay()
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
        retryConfigBuilder.constantDelay(duration, randomizationFactor)
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
        retryConfigBuilder.linearDelay(initialDelay, multiplier, maxDelay, randomizationFactor)
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
        retryConfigBuilder.exponentialDelay(initialDelay, multiplier, maxDelay, randomizationFactor)
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
        retryConfigBuilder.customDelay(delayStrategy)
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
        retryConfigBuilder.customDelayProvider(delayProvider)
    }

    /**
     * Disables the retry plugin.
     */
    fun disableRetry() {
        maxAttempts = 1
        retryConfigBuilder.disableExceptionHandler()
    }

    override fun build(): RetryPluginConfig {
        retryConfigBuilder.retryIf { aggregateRetryPredicates(it) }
        return RetryPluginConfig(
            retryConfig = retryConfigBuilder.build(),
            modifyRequestOnRetry = modifyRequest,
            retryOnCallPredicate = retryOnCallPredicate
        )
    }

    /**
     * Aggregates all configured retry predicates to determine if the HTTP call should be retried based on the caught throwable.
     */
    private fun aggregateRetryPredicates(throwable: Throwable): Boolean {
        if (throwable is RetryOnCallException) return true
        return retryPredicate(throwable)
    }

}

/**
 * Indicates whether the exception is a timeout exception.
 * See [HttpTimeout] plugin for more information.
 */
private fun Throwable.isTimeoutException(): Boolean {
    val exception = unwrapCancellationException()
    val possibleTimeoutExceptions = listOf(
        // The request timeout is the time period required to process an HTTP
        // call (from sending a request to receiving a response).
        HttpRequestTimeoutException::class,
        // This exception is thrown in case connect timeout exceeded.
        ConnectTimeoutException::class,
        // This exception is thrown in case socket timeout (read or write) exceeded.
        SocketTimeoutException::class,
    )
    return possibleTimeoutExceptions.any { it.isInstance(exception) }
}
