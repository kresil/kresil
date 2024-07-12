package kresil.ktor.server.plugins.ratelimiter

import io.ktor.server.application.*
import io.ktor.util.logging.*

private val logger = KtorSimpleLogger("kresil.ktor.server.plugins.ratelimiter.KresilRouteRateLimiterPlugin")

val KresilRouteRateLimiterPlugin = createRouteScopedPlugin(
    name = "KresilRouteRateLimiterPlugin",
    createConfiguration = createRateLimiterConfigBuilder(),
    body = { buildRateLimiterPlugin(logger) }
)
