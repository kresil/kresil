package kresil.ktor.plugins.retry.client

import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import kotlinx.coroutines.CancellationException
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
        try {
            retry.onEvent { event ->
                println("Event: $event")
            }
            lateinit var call: HttpClientCall
            retry.executeSupplier {
                call = proceed(request)
                println("Call: ${call.response}")
                if (pluginConfig.shouldRetryOnCall(call.request, call.response)) {
                    throw RetryOnCallException()
                }
            }
            call.also { retry.cancelListeners() }
        } catch (e: Exception) {
            println("really bro, I've found an exception: $e")
            throw e
        }
    }
}
