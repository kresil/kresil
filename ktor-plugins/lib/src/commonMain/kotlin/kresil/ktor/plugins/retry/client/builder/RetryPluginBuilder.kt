package kresil.ktor.plugins.retry.client.builder

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import kresil.lib.retry.config.RetryConfigBuilder

// TODO: add modify request between retries
// TODO: propagate cancellation of subsequent requests in case of cancellation of the first request
// TODO: add support for retry on a per-request level
class RetryPluginBuilder : RetryConfigBuilder() {

    var shouldRetryOnCall: (HttpRequest, HttpResponse) -> Boolean = { _, _ -> false }

    fun retryOnCall(block: (HttpRequest, HttpResponse) -> Boolean) {
        shouldRetryOnCall = block
    }

    fun retryOnServerErrors() {
        retryOnCall { _, response ->
            response.status.value in 500..599
        }
    }

    fun retryOnTimeout() = addRetryPredicate { it.isTimeoutException() }
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
