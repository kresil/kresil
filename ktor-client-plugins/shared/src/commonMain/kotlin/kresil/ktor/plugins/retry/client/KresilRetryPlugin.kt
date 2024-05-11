package kresil.ktor.plugins.retry.client

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.util.*
import kotlinx.coroutines.CompletableJob
import kresil.ktor.plugins.retry.client.config.RetryPluginConfig
import kresil.ktor.plugins.retry.client.config.RetryPluginConfigBuilder
import kresil.ktor.plugins.retry.client.exceptions.RetryOnCallException
import kresil.retry.Retry
import kresil.retry.config.retryConfig

// TODO: find another way to store global configuration
private lateinit var globalConfig: RetryPluginConfig

/**
 * A plugin that enables the client to retry failed requests based on the Kresil Retry mechanism
 * configuration and the [HttpRequestRetry] plugin provided by Ktor.
 * Configuration can be done globally when installing the plugin,
 * or on a per-request basis with the [kRetry] function.
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
 *      retryOnException { it is NetworkError }
 *      constantDelay(2.seconds)
 *      // customDelay { attempt, lastThrowable -> ... }
 *      // noDelay()
 *      modifyRequestOnRetry { request, attempt ->
 *           request.headers.append("X_RETRY_COUNT", "$attempt")
 *      }
 * }
 * ```
 */
val KresilRetryPlugin = createClientPlugin(
    name = "KresilRetryPlugin",
    createConfiguration = {
        RetryPluginConfigBuilder(baseConfig = defaultRetryPluginConfig)
    }
) {
    globalConfig = pluginConfig.build()
    on(Send) { request ->  // is invoked every time a request is sent
        val requestPluginBuilder: RetryPluginConfigBuilder = request.attributes
            .getOrNull(RetryPluginConfigBuilderPerRequestAttributeKey)
            ?: pluginConfig
        val requestPluginConfig: RetryPluginConfig = requestPluginBuilder.build()
        println("Request plugin config: $requestPluginConfig")
        val retry = Retry(requestPluginConfig.retryConfig)
        retry.onEvent { event ->
            println("Received event: $event")
        }
        lateinit var call: HttpClientCall
        try {
            retry.executeSupplier {
                val subRequest = copyRequestAndPropagateCompletion(request)
                requestPluginConfig.modifyRequestOnRetry(subRequest)
                call = proceed(subRequest) // proceed with the modified request
                println("Request headers: ${call.request.headers}")
                println("Response call: ${call.response}")
                if (requestPluginConfig.retryOnCallPredicate(call.request, call.response)) {
                    throw RetryOnCallException()
                }
            }
        } finally {
            retry.cancelListeners()
        }
        call
    }
}

/**
 * Creates a copy of the given HTTP request builder, preserving its configuration but not the
 * execution context, and guarantees that any completion or failure of the original request is
 * mirrored onto the copy.
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

/**
 * Configures the [KresilRetryPlugin] on a per-request level.
 * The configuration declared in this block will override the global configuration, or inherit from it if not specified.
 *
 * **Note:** This method requires the [KresilRetryPlugin] to be installed.
 * @param disable if set to `true`, the request will not be retried
 * @param configure the configuration block to apply to this request
 */
fun HttpRequestBuilder.kRetry(disable: Boolean = false, configure: RetryPluginConfigBuilder.() -> Unit = {}) {
    val builder = RetryPluginConfigBuilder(baseConfig = globalConfig).apply(configure)
    if (disable) {
        builder.disableRetry()
    }
    attributes.put(RetryPluginConfigBuilderPerRequestAttributeKey, builder)
}

private val RetryPluginConfigBuilderPerRequestAttributeKey =
    AttributeKey<RetryPluginConfigBuilder>(
        "RetryPluginConfigBuilderPerRequestAttributeKey"
    )

private val defaultRetryPluginConfig = RetryPluginConfig(
    retryConfig = retryConfig {
        maxAttempts = 8
        retryIf { it is RetryOnCallException }
        retryOnResult { false }
        noDelay()
    },
    modifyRequestOnRetry = { println("No modification on retry") },
    retryOnCallPredicate = { _, _ -> false }
)
