package kresil.ktor.plugins.client

import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import kresil.retry.Retry
import kresil.retry.config.RetryConfig
import kresil.retry.config.RetryConfigBuilder

val KresilRetryPlugin = createClientPlugin(
    name = "KresilRetryPlugin",
    createConfiguration = ::RetryConfigBuilder
) {
    val retryConfig: RetryConfig = pluginConfig.build()
    val retry = Retry(retryConfig)
    on(Send) { request -> // is invoked every time a request is sent.
        retry.onEvent { event ->
            println("Event: $event")
        }
        lateinit var call: HttpClientCall
        try {
            retry.executeSupplier {
                println("Retrying request: $request")
                call = proceed(request)
                println("Response: ${call.response}")
            }
        } catch (cause: Throwable) {
            println("Failed to execute request: $cause")
        }
        call
    }
}
