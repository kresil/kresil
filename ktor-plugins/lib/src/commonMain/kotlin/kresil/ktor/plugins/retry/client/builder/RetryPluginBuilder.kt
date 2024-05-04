package kresil.ktor.plugins.retry.client.builder

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import kresil.lib.retry.config.RetryConfigBuilder
import kresil.ktor.plugins.retry.client.KresilRetryPlugin

// TODO: add support for listeners callbacks (what is the use case for this?)
// TODO: add support for retry on a per-request level
//  (might not be possible since the retry is immutable upon creation, consider changing this implementation)
/**
 * Builder for configuring the [KresilRetryPlugin].
 */
class RetryPluginBuilder : RetryConfigBuilder() {

    var shouldRetryOnCall: (HttpRequest, HttpResponse) -> Boolean = { _, _ -> false }
    var modifyRequest: (HttpRequestBuilder) -> Unit = { }

    init {
        // inherits default retry configuration
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
    fun retryOnTimeout() = addRetryPredicate { it.isTimeoutException() }

    /**
     * Modifies the request between retries, before it is sent.
     * The block receives the [HttpRequestBuilder] and the current retry attempt as arguments.
     */
    fun modifyRequestOnRetry(block: (builder: HttpRequestBuilder, attempt: Int) -> Unit) {
        beforeOpCallback { attempt ->
            modifyRequest = { builder ->
                block(builder, attempt)
            }
        }
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
