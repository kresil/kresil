package kresil.ktor.plugins.client

import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kresil.lib.retry.Retry
import kresil.lib.retry.config.RetryConfig
import kresil.lib.retry.config.RetryConfigBuilder

val KresilRetryPlugin = createClientPlugin(
    name = "KresilRetryPlugin",
    createConfiguration = ::RetryPluginBuilder
) {
    pluginConfig.addRetryPredicate { it is RetryOnCallException }
    val retryConfig: RetryConfig = pluginConfig.build()
    val retry = Retry(retryConfig)
    on(Send) { request -> // is invoked every time a request is sent.
        retry.onEvent { event ->
            println("Event: $event")
        }
        lateinit var call: HttpClientCall
        retry.executeSupplier {
            call = proceed(request)
            println("Call: ${call.response}")
            if (pluginConfig.shouldRetryOnCall.invoke(call.request, call.response)) {
                throw RetryOnCallException()
            }
        }
        call.also { retry.cancelListeners() }
    }
}

// TODO: add modify request between retries
// TODO: add should retry on exception support
// TODO: propagate cancellation of subsequent requests in case of cancellation of the first request
// TODO: add support for timeout requests
// TODO: add support for retry on a per-request level
class RetryPluginBuilder : RetryConfigBuilder() {

    var shouldRetryOnException: ((HttpRequestBuilder, Throwable) -> Boolean)? = null
    var shouldRetryOnCall: (HttpRequest, HttpResponse) -> Boolean = { _, _ -> false }

    fun retryOnCall(block: (HttpRequest, HttpResponse) -> Boolean) {
        shouldRetryOnCall = block
    }

    fun retryOnServerErrors() {
        retryOnCall { _, response ->
            response.status.value in 500..599
        }
    }

    fun retryOnExceptionIf(predicate: (HttpRequestBuilder, Throwable) -> Boolean) {
        shouldRetryOnException = predicate
    }
}

internal class RetryOnCallException : Exception()
