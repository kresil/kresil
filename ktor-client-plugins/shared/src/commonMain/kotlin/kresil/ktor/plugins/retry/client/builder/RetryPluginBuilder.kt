package kresil.ktor.plugins.retry.client.builder

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import kresil.ktor.plugins.retry.client.KresilRetryPlugin
import kresil.ktor.plugins.retry.client.exceptions.RetryOnCallException
import kresil.retry.config.RetryConfig
import kresil.retry.config.RetryConfigBuilder
import kresil.retry.config.RetryPredicate
import kresil.retry.delay.RetryDelayProvider
import kresil.retry.delay.RetryDelayStrategy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Builder for configuring the [KresilRetryPlugin].
 */
class RetryPluginBuilder(private val retryConfigBuilder: RetryConfigBuilder) {

    private var retryPredicateList: MutableList<RetryPredicate> = mutableListOf()
    var shouldRetryOnCall: (HttpRequest, HttpResponse) -> Boolean = { _, _ -> false }
    var modifyRequest: (HttpRequestBuilder) -> Unit = { }

    init {
        exponentialDelay()
        retryOnServerErrors()
    }

    /**
     * Configures a predicate to determine if an HTTP call should be retried based on the respective request and response.
     * @see retryOnServerErrors
     */
    fun retryOnCall(block: (request: HttpRequest, response: HttpResponse) -> Boolean) {
        shouldRetryOnCall = block
    }

    /**
     * Retries the HTTP call if the response status code is in the range **500..599**.
     */
    fun retryOnServerErrors() {
        retryOnCall { _, response ->
            response.status.value in 500..599
        }
    }

    /**
     * Retries the HTTP call if the exception thrown is a timeout exception.
     * See [HttpTimeout] plugin for more information on possible timeout exceptions.
     * If this method is used, [HttpTimeout] plugin should be installed after this plugin.
     */
    fun retryOnTimeout() = retryPredicateList.add(Throwable::isTimeoutException)

    /**
     * Modifies the request between retries, before it is sent.
     * The block receives the [HttpRequestBuilder] and the current retry attempt as arguments.
     */
    fun modifyRequestOnRetry(block: (builder: HttpRequestBuilder, attempt: Int) -> Unit) {
        retryConfigBuilder.beforeOperCallback { attempt ->
            modifyRequest = { builder ->
                block(builder, attempt)
            }
        }
    }

    /**
     * The maximum number of attempts **(including the initial call as the first attempt)**.
     */
    var maxAttempts: Int = retryConfigBuilder.maxAttempts
        set(value) {
            retryConfigBuilder.maxAttempts = value
            field = value
        }

    /**
     * Configures the retry predicate.
     * The predicate is used to determine if, based on the caught throwable, the operation should be retried.
     * @param predicate the predicate to use.
     */
    fun retryOnException(predicate: RetryPredicate) {
        retryPredicateList.add(predicate)
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
    fun constantDelay(duration: Duration) {
        retryConfigBuilder.constantDelay(duration)
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
     * @see [customDelayProvider]
     * @see [noDelay]
     */
    fun exponentialDelay(
        initialDelay: Duration = 500L.milliseconds,
        multiplier: Double = 2.0, // not using constant to be readable for the user
        maxDelay: Duration = 1.minutes,
    ) {
        retryConfigBuilder.exponentialDelay(initialDelay, multiplier, maxDelay)
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
        retryConfigBuilder.customDelay(delayStrategy)
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
        retryConfigBuilder.customDelayProvider(delayProvider)
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
        retryConfigBuilder.noDelay()
    }

    fun build(): RetryConfig {
        retryConfigBuilder.retryIf { joinAllConfiguredPredicates(it) }
        return retryConfigBuilder.build()
    }

    private fun joinAllConfiguredPredicates(throwable: Throwable): Boolean {
        // add builder custom exception to be thrown on retry
        val retryBuilderPredicate: RetryPredicate = { throwable is RetryOnCallException }
        retryPredicateList.add(retryBuilderPredicate)
        return (retryPredicateList.any { it(throwable) })
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
