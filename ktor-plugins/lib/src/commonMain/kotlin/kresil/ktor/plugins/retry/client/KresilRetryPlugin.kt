package kresil.ktor.plugins.retry.client

import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kresil.ktor.plugins.retry.client.builder.RetryPluginBuilder
import kresil.ktor.plugins.retry.exceptions.RetryOnCallException
import kresil.lib.retry.Retry
import kresil.lib.retry.config.RetryConfig

val KresilRetryPlugin = createClientPlugin(
    name = "KresilRetryPlugin",
    createConfiguration = ::RetryPluginBuilder
) {
    pluginConfig.addRetryPredicate { it is RetryOnCallException && it is CancellationException }
    val retryConfig: RetryConfig = pluginConfig.build()
    val retry = Retry(retryConfig)
    on(Send) { request -> // is invoked every time a request is sent
        retry.onEvent { event ->
            println("Event: $event")
        }
        lateinit var call: HttpClientCall
        retry.executeSupplier {
            val subRequest = copyRequestAndPropagateCompletion(request)
            pluginConfig.modifyRequest(subRequest)
            call = proceed(subRequest)
            println("Request Headers: ${call.request.headers}")
            println("Call: ${call.response}")
            if (pluginConfig.shouldRetryOnCall(call.request, call.response)) {
                throw RetryOnCallException()
            }
        }
        call.also { retry.cancelListeners() }
    }
}

private fun copyRequestAndPropagateCompletion(request: HttpRequestBuilder): HttpRequestBuilder {
    val subRequest = HttpRequestBuilder().takeFrom(request)
    request.executionContext.invokeOnCompletion { cause ->
        val subRequestJob = subRequest.executionContext as CompletableJob
        if (cause == null) {
            subRequestJob.complete()
        } else subRequestJob.completeExceptionally(cause)
    }
    return subRequest
}

