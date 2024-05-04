package kresil.ktor.plugins.retry.client

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import kotlinx.coroutines.CompletableJob
import kresil.ktor.plugins.retry.client.builder.RetryPluginBuilder
import kresil.ktor.plugins.retry.client.exceptions.RetryOnCallException
import kresil.lib.retry.Retry

/**
 * A plugin that enables the client to retry failed requests based on the Kresil Retry mechanism configuration.
 * Based on the [HttpRequestRetry] plugin provided by Ktor.
 * Examples of usage:
 * ```
 * // use predefined retry policies
 * install(KresilRetryPlugin) {
 *      retryOnServerErrors()
 *      exponentialDelay()
 * }
 *
 * // use custom policies
 * install(KresilRetryPlugin) {
 *      maxRetries = 5
 *      retryOnTimeout()
 *      addRetryPredicate { it is NetworkError }
 *      constantDelay(2.seconds)
 *      // customDelay { attempt, lastThrowable -> ... }
 *      modifyRequestOnRetry { request, attempt ->
 *           request.headers.append("X_RETRY_COUNT", "$attempt")
 *      }
 * }
 * ```
 */
val KresilRetryPlugin = createClientPlugin(
    name = "KresilRetryPlugin",
    createConfiguration = ::RetryPluginBuilder
) {
    pluginConfig.addRetryPredicate { it is RetryOnCallException }
    val retryConfig = pluginConfig.build()
    val retry = Retry(retryConfig)
    on(Send) { request -> // is invoked every time a request is sent
        retry.onEvent { event ->
            println("Received event: $event")
        }
        lateinit var call: HttpClientCall
        retry.executeSupplier {
            val subRequest = copyRequestAndPropagateCompletion(request)
            pluginConfig.modifyRequest(subRequest)
            call = proceed(subRequest) // proceed with the modified request
            println("Request headers: ${call.request.headers}")
            println("Response call: ${call.response}")
            if (pluginConfig.shouldRetryOnCall(call.request, call.response)) {
                throw RetryOnCallException()
            }
        }
        call
    }
    onClose { retry.cancelListeners() }
}

/**
 * Creates a copy of the given HTTP request builder, preserving its configuration, and guarantees that any completion or failure of the original request is mirrored onto the copy.
 */
private fun copyRequestAndPropagateCompletion(request: HttpRequestBuilder): HttpRequestBuilder {
    val subRequest = HttpRequestBuilder().takeFrom(request)
    request.executionContext.invokeOnCompletion { cause ->
        val subRequestJob = subRequest.executionContext as CompletableJob
        if (cause == null) subRequestJob.complete()
        else subRequestJob.completeExceptionally(cause)
    }
    return subRequest
}
