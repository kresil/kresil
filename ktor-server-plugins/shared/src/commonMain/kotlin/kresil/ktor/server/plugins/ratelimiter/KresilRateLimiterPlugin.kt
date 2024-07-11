package kresil.ktor.server.plugins.ratelimiter

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.logging.*
import kresil.ktor.server.plugins.ratelimiter.config.*
import kresil.ratelimiter.KeyedRateLimiter
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
    val keyedRateLimiter = KeyedRateLimiter<String>(
        config = pluginConfig.rateLimiterConfig
    )

    on(pluginConfig.interceptPhase) { call ->
        logger.info("Request received: ${call.request.uri}")
        if (pluginConfig.excludePredicate(call)) {
            logger.info("Request excluded from rate limiting: ${call.request.uri}")
            return@on
        }
        val key = pluginConfig.keyResolver(call)
        val rateLimiter = keyedRateLimiter.getRateLimiter(key)
        logger.info("Acquiring permits for key: $key")
        rateLimiter.acquire(1, pluginConfig.rateLimiterConfig.baseTimeoutDuration)
    }

    onCallRespond { call, _ ->
        logger.info("Request successfully processed for key: ${pluginConfig.keyResolver(call)}")
        pluginConfig.onSuccessCall(call)
    }

    on(CallFailed) { call, exception ->
        val key = pluginConfig.keyResolver(call)
        logger.info("Request failed for key: $key", exception)
        val rateLimiter = keyedRateLimiter.getRateLimiter(key)
        logger.info("Releasing permits for key: $key")
        rateLimiter.release(1)
        if (exception is RateLimiterRejectedException) {
            logger.info("Request failed due to rate limit for key: $key, retry after: ${exception.retryAfter}")
            pluginConfig.onRejectedCall(call, exception.retryAfter)
        }
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
    onSuccessCall = { },
    excludePredicate = { false },
    interceptPhase = CallSetup
)
