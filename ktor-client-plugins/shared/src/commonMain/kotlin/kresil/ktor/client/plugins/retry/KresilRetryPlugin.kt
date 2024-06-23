package kresil.ktor.client.plugins.retry

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.util.logging.*
import kotlinx.coroutines.CompletableJob
import kresil.ktor.client.plugins.retry.config.RetryPluginConfig
import kresil.ktor.client.plugins.retry.config.RetryPluginConfigBuilder
import kresil.ktor.client.plugins.retry.exceptions.RetryOnCallException
import kresil.retry.Retry
import kresil.retry.config.retryConfig

private val logger = KtorSimpleLogger("kresil.ktor.client.plugins.retry.KresilRetryPlugin")

// TODO: find another way to store global configuration
private lateinit var globalConfig: RetryPluginConfig

/**
 * A plugin that enables the client to retry failed requests based on the Kresil [Retry] mechanism
 * configuration and the [HttpRequestRetry] plugin provided by Ktor.
 * Configuration can be done globally when installing the plugin,
 * and on a per-request basis with the [kRetry] function.
 *
 * Examples of usage:
 * ```
 * // use predefined retry policies
 * install(KresilRetryPlugin)
 *
 * // use custom policies
 * install(KresilRetryPlugin) {
 *      maxRetries = 5
 *      retryOnException { it is NetworkError }
 *      // retryOnTimeout()
 *      constantDelay(2.seconds)
 *      // noDelay()
 *      // customDelay { attempt, context -> ... }
 *      modifyRequestOnRetry { request, attempt ->
 *           request.headers.append("X_RETRY_COUNT", "$attempt")
 *      }
 * }
 *
 * // disable retry for a specific request
 * client.post {
 *     kRetry(disable = true)
 * }
 * ```
 * @see Retry
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
        val retry = Retry(requestPluginConfig.retryConfig)
        retry.onEvent { event -> logger.info("Retry event: $event") }
        lateinit var call: HttpClientCall
        try {
            retry.executeCtxSupplier<Unit, Unit> { ctx ->
                val subRequest = copyRequestAndPropagateCompletion(request)
                if (ctx.attempt > 0) requestPluginConfig.modifyRequestOnRetry(subRequest, ctx.attempt)
                call = proceed(subRequest) // proceed with the modified request
                logger.info("Request headers: ${call.request.headers}")
                logger.info("Response call: ${call.response}")
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
        maxAttempts = 3
        exponentialDelay()
    },
    modifyRequestOnRetry = { _, _ -> },
    retryOnCallPredicate = { _, response -> response.status.value in 500..599 }
)
