package kresil.ktor.server.plugins.ratelimiter.config

import io.ktor.server.application.*
import kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin
import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.config.RateLimiterConfig
import kotlin.time.Duration

/**
 * A function that construct a key for rate limiting from the application call.
 */
internal typealias KeyResolver = suspend (call: ApplicationCall) -> String

/**
 * A callback that handles a request that has been rejected due to rate limiting.
 */
internal typealias OnRejectedCall = suspend (call: ApplicationCall, retryAfterDuration: Duration) -> Unit

/**
 * A callback that handles a successful request.
 */
internal typealias OnSuccessCall = suspend (call: ApplicationCall) -> Unit

/**
 * A function that determines whether a specific request should be excluded from rate limiting.
 * @return `true` if the request should be excluded from rate limiting, `false` otherwise.
 */
internal typealias ExcludeFromRateLimiting = suspend (call: ApplicationCall) -> Boolean

/**
 * A Ktor [Hook] that intercepts a phase in the application pipeline to apply rate limiting.
 */
internal typealias InterceptPhase = Hook<suspend (ApplicationCall) -> Unit>


/**
 * Configuration for the [KresilRateLimiterPlugin].
 * @param rateLimiterConfig The configuration for the Kresil [RateLimiter] mechanism.
 * @param keyResolver A function to resolve the key for rate limiting from the application call.
 * @param onRejectedCall A callback to handle requests that are rejected due to rate limiting.
 * @param onSuccessCall A callback to handle successful requests.
 * @param excludeFromRateLimiting A function to determine if a request should be excluded from rate limiting.
 * @param interceptPhase The phase in the application lifecycle where rate limiting is applied.
 */
data class RateLimiterPluginConfig(
    val rateLimiterConfig: RateLimiterConfig,
    val keyResolver: KeyResolver,
    val onRejectedCall: OnRejectedCall,
    val onSuccessCall: OnSuccessCall,
    val excludeFromRateLimiting: ExcludeFromRateLimiting,
    val interceptPhase: InterceptPhase,
)
