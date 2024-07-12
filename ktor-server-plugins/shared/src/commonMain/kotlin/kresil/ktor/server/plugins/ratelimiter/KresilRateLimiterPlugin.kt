package kresil.ktor.server.plugins.ratelimiter

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import kresil.ktor.server.plugins.ratelimiter.config.RateLimiterPluginConfig
import kresil.ktor.server.plugins.ratelimiter.config.RateLimiterPluginConfigBuilder
import kresil.ratelimiter.KeyedRateLimiter
import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.FixedWindowCounter
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KtorSimpleLogger("kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin")

val KresilRateLimiterPlugin = createApplicationPlugin(
    name = "KresilRateLimiterPlugin",
    createConfiguration = {
        RateLimiterPluginConfigBuilder(baseConfig = defaultRateLimiterPluginConfig)
    }
) {
    val pluginConfig = pluginConfig.build()
    val keyedRateLimiter = KeyedRateLimiter<Any>(
        config = pluginConfig.rateLimiterConfig
    )

    on(pluginConfig.interceptPhase) { call ->
        logger.info("Request received: ${call.request.uri}")
        if (pluginConfig.excludePredicate(call)) {
            logger.info("Request excluded from rate limiting: ${call.request.uri}")
            // place something in the response to indicate that the request was not rate limited
            call.attributes.put(RequestWasNotRateLimitedKey, true)
            return@on
        }
        val key = pluginConfig.keyResolver(call)
        val rateLimiter = keyedRateLimiter.getRateLimiter(key)
        rateLimiter.onEvent { event -> // TODO: missing implementation
            logger.info("Rate limiter event: $event")
        }
        val permits = pluginConfig.callWeight(call)
        logger.info("Acquiring ($permits) permits for key: [$key]")
        rateLimiter.acquire(permits, pluginConfig.rateLimiterConfig.baseTimeoutDuration)
        call.attributes.put(
            key = RequestWentThroughRateLimiterKey,
            value = RateLimiterAcquisitionData(rateLimiter, permits, key)
        )
    }
    onCall {

    }
    onCallRespond { call, _ ->
        if (call.attributes.contains(RequestWasNotRateLimitedKey)) {
            call.attributes.remove(RequestWasNotRateLimitedKey)
            return@onCallRespond
        }
        val acquisitionData = call.attributes.getOrNull(RequestWentThroughRateLimiterKey)
            // return, as a request cannot release permits if it did not acquire them
            ?: return@onCallRespond

        val (rateLimiter, permits, key) = acquisitionData
        logger.info("Request successfully processed for key: [${pluginConfig.keyResolver(call)}]")
        logger.info("Releasing ($permits) permits for key: [$key]")
        rateLimiter.release(permits)
        pluginConfig.onSuccessCall(call)
        logger.info("Success response headers: ${call.response.headers.allValues()}")
    }

    on(CallFailed) { call, exception ->
        logger.info("Failed response call: ${call.response} with exception: $exception")
        if (exception is RateLimiterRejectedException) {
            logger.info("Request failed due to rate limit for key: [${pluginConfig.keyResolver(call)}], retry after: ${exception.retryAfter}")
            pluginConfig.onRejectedCall(call, exception.retryAfter)
        }
        logger.info("Failed response headers: ${call.response.headers.allValues()}")
    }

}

private val defaultRateLimiterPluginConfig = RateLimiterPluginConfig(
    rateLimiterConfig = rateLimiterConfig {
        algorithm(
            FixedWindowCounter(
                totalPermits = 1000,
                replenishmentPeriod = 1.minutes,
                queueLength = 0
            )
        )
        baseTimeoutDuration = 3.seconds
        onRejected { throw it }
    },
    keyResolver = { call ->
        call.request.local.remoteHost + call.request.userAgent()
    },
    onRejectedCall = { call, retryAfter ->
        call.response.header(
            name = "Retry-After",
            value = retryAfter.inWholeSeconds.toString()
        )
        call.respond(
            status = HttpStatusCode.TooManyRequests,
            message = "Rate limit exceeded. Try again in $retryAfter."
        )
    },
    onSuccessCall = { call ->
        call.response.header("X-Rate-Limited", "false")
    },
    excludePredicate = { false },
    interceptPhase = CallSetup,
    callWeight = { 1 }
)

private val RequestWasNotRateLimitedKey =
    AttributeKey<Boolean>("RequestWasNotRateLimitedKey")

private val RequestWentThroughRateLimiterKey =
    AttributeKey<RateLimiterAcquisitionData>("RequestWentThroughRateLimiterKey")

private data class RateLimiterAcquisitionData(
    val rateLimiter: RateLimiter,
    val permits: Int,
    val key: Any
)
