package kresil.ktor.server.plugins.ratelimiter.config

import io.ktor.server.application.*
import kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin
import kresil.ratelimiter.RateLimiter
import kresil.ratelimiter.config.RateLimiterConfig
import kotlin.time.Duration

/**
 * Calculates the weight of a call as an integer.
 * The weight determines how many permits a call consumes in rate limiting.
 */
internal typealias CallWeight = suspend (call: ApplicationCall) -> Int

/**
 * A function that resolves a key for rate limiting from the application call (e.g., an IP address).
 */
internal typealias KeyResolver = suspend (call: ApplicationCall) -> Any

/**
 * A callback that handles a request that has been rejected due to rate limiting.
 */
internal typealias OnRejectedCall = suspend (call: ApplicationCall, retryAfterDuration: Duration) -> Unit

/**
 * A callback that handles a successful request that has passed rate limiting.
 */
internal typealias OnSuccessCall = suspend (call: ApplicationCall) -> Unit

/**
 * A predicate that determines whether a specific request should be excluded from rate limiting.
 * @return `true` if the request should be excluded from rate limiting, `false` otherwise.
 */
internal typealias ExcludePredicate = suspend (call: ApplicationCall) -> Boolean

/**
 * A Ktor [Hook] that intercepts a phase in the application pipeline to apply rate limiting.
 */
internal typealias InterceptPhase = Hook<suspend (ApplicationCall) -> Unit>


/**
 * Configuration for the [KresilRateLimiterPlugin].
 * @param rateLimiterConfig The configuration for the Kresil [RateLimiter] mechanism.
 * @param keyResolver A function to resolve the key for rate limiting from the application call.
 * @param onRejectedCall A callback to handle requests that are rejected due to rate limiting.
 * @param onSuccessCall A callback to handle successful requests that have passed rate limiting.
 * @param excludePredicate A predicate to determine if a request should be excluded from rate limiting.
 * @param interceptPhase The phase in the application pipeline to intercept for rate limiting.
 * @param callWeight A function to calculate the weight of a call for rate limiting.
 * The weight determines how many permits a call consumes.
 */
data class RateLimiterPluginConfig(
    val rateLimiterConfig: RateLimiterConfig,
    val keyResolver: KeyResolver,
    val onRejectedCall: OnRejectedCall,
    val onSuccessCall: OnSuccessCall,
    val excludePredicate: ExcludePredicate,
    val interceptPhase: InterceptPhase,
    val callWeight: CallWeight,
)
