package kresil.ktor.plugins.retry.client

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.util.*
import kotlinx.coroutines.CompletableJob
import kresil.ktor.plugins.retry.client.builder.ModifyRequestOnRetry
import kresil.ktor.plugins.retry.client.builder.RetryOnCallPredicate
import kresil.ktor.plugins.retry.client.builder.RetryPluginBuilder
import kresil.ktor.plugins.retry.client.builder.RetryPluginConfig
import kresil.ktor.plugins.retry.client.builder.RetryPluginPerReqBuilder
import kresil.ktor.plugins.retry.client.exceptions.RetryOnCallException
import kresil.retry.Retry
import kresil.retry.config.BeforeOperationCallback
import kresil.retry.config.RetryConfig
import kresil.retry.config.RetryPredicate
import kresil.retry.config.SuspendRetryDelayStrategy

// TODO: update comments for per-request configuration
// TODO: kRetry requires plugin to be installed first (warned in the docs)
/**
 * A plugin that enables the client to retry failed requests based on the Kresil Retry mechanism configuration and the [HttpRequestRetry] plugin provided by Ktor.
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
    createConfiguration = ::RetryPluginBuilder
) {
    val globalPluginConfig = pluginConfig.build()
    on(Send) { request ->  // is invoked every time a request is sent
        // grab the per-request configuration settings
        val maxAttemptsSource = if (request.attributes.contains(RetryMaxAttemptsPerRequestAttributeKey))
            "Per-request" else "Global"
        println("Max attempts source: $maxAttemptsSource")
        val maxAttempts = request.attributes.getOrNull(RetryMaxAttemptsPerRequestAttributeKey) ?: globalPluginConfig.retryConfig.maxAttempts
        println("Max attempts: $maxAttempts")
        val retryPredicateSource = if (request.attributes.contains(RetryPredicatePerRequestAttributeKey))
            "Per-request" else "Global"
        println("Retry predicate source: $retryPredicateSource")
        val retryPredicate = request.attributes.getOrNull(RetryPredicatePerRequestAttributeKey) ?: globalPluginConfig.retryConfig.retryPredicate
        val delayStrategySource = if (request.attributes.contains(RetryDelayStrategyPerRequestAttributeKey))
            "Per-request" else "Global"
        println("Delay strategy source: $delayStrategySource")
        val delayStrategy = request.attributes.getOrNull(RetryDelayStrategyPerRequestAttributeKey) ?: globalPluginConfig.retryConfig.delayStrategy
        val beforeOperationCallBackSource = if (request.attributes.contains(BeforeOperationCallbackPerRequestAttributeKey))
            "Per-request" else "Global"
        println("Before operation callback source: $beforeOperationCallBackSource")
        val beforeOperationCallback = request.attributes.getOrNull(BeforeOperationCallbackPerRequestAttributeKey) ?: globalPluginConfig.retryConfig.beforeOperationCallback
        val shouldRetryOnCallSource = if (request.attributes.contains(RetryOnCallPerRequestAttributeKey))
            "Per-request" else "Global"
        println("Should retry on call source: $shouldRetryOnCallSource")
        val shouldRetryOnCall = request.attributes.getOrNull(RetryOnCallPerRequestAttributeKey) ?: globalPluginConfig.retryOnCallPredicate
        val modifyRequestSource = if (request.attributes.contains(ModifyRequestPerRequestAttributeKey))
            "Per-request" else "Global"
        println("Modify request source: $modifyRequestSource")
        val modifyRequest = request.attributes.getOrNull(ModifyRequestPerRequestAttributeKey) ?: globalPluginConfig.modifyRequestOnRetry

        // get the immutable global retry config from the builder
        println("Global configuration: $globalPluginConfig")
        val requestRetryConfig = RetryPluginConfig(
            RetryConfig(
                maxAttempts,
                retryPredicate,
                globalPluginConfig.retryConfig.retryOnResultPredicate,
                delayStrategy,
                beforeOperationCallback,
            ),
            modifyRequest,
            shouldRetryOnCall
        )
        val retry = Retry(globalPluginConfig.retryConfig)
        retry.onEvent { event ->
            println("Received event: $event")
        }
        lateinit var call: HttpClientCall
        retry.executeSupplier {
            val subRequest = copyRequestAndPropagateCompletion(request)
            requestRetryConfig.modifyRequestOnRetry(subRequest)
            call = proceed(subRequest) // proceed with the modified request
            println("Request headers: ${call.request.headers}")
            println("Response call: ${call.response}")
            if (requestRetryConfig.retryOnCallPredicate(call)) {
                throw RetryOnCallException()
            }
        }
        call
    }
    // onClose { retry.cancelListeners() }
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
 * The configuration declared in this block will override the global configuration, or inherit it if not specified.
 */
fun HttpRequestBuilder.kRetry(block: RetryPluginPerReqBuilder.() -> Unit) {
    val retryPerReqBuilder = RetryPluginPerReqBuilder().apply(block)
    val configuration = retryPerReqBuilder.build()
    println("Per-request configuration: $configuration")
    // save the per-request configuration settings
    /*attributes.put(RetryMaxAttemptsPerRequestAttributeKey, configuration.maxAttempts)
    attributes.put(RetryPredicatePerRequestAttributeKey, configuration.retryPredicate)
    attributes.put(RetryDelayStrategyPerRequestAttributeKey, configuration.delayStrategy)
    attributes.put(BeforeOperationCallbackPerRequestAttributeKey, configuration.beforeOperationCallback)
    attributes.put(RetryOnCallPerRequestAttributeKey, configuration.retryOnCallPredicate)
    attributes.put(ModifyRequestPerRequestAttributeKey, configuration.modifyRequest)*/
}

private val RetryMaxAttemptsPerRequestAttributeKey =
    AttributeKey<Int>("MaxAttemptsPerRequestAttributeKey")
private val RetryPredicatePerRequestAttributeKey =
    AttributeKey<RetryPredicate>("RetryPredicatePerRequestAttributeKey")
private val RetryDelayStrategyPerRequestAttributeKey =
    AttributeKey<SuspendRetryDelayStrategy>("RetryDelayStrategyPerRequestAttributeKey")
private val BeforeOperationCallbackPerRequestAttributeKey =
    AttributeKey<BeforeOperationCallback>("BeforeOperationCallbackPerRequestAttributeKey")
private val RetryOnCallPerRequestAttributeKey =
    AttributeKey<RetryOnCallPredicate>("RetryOnCallPerRequestAttributeKey")
private val ModifyRequestPerRequestAttributeKey =
    AttributeKey<ModifyRequestOnRetry>(
        "ModifyRequestPerRequestAttributeKey"
    )
