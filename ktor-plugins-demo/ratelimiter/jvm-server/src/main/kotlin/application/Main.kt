package application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

private const val PORT = 8080

private object ServerRoutes {
    const val RATE_LIMITED = "/rate-limited"
    const val UNRATE_LIMITED = "/unrate-limited"
}

fun main() {
    embeddedServer(Netty, port = PORT) {
        install(CORS) {
            HttpMethod.DefaultMethods.forEach {
                allowMethod(it)
            }
            allowHeaders { true }
            anyHost()
        }
        install(KresilRateLimiterPlugin) {
            algorithm(
                RateLimitingAlgorithm.FixedWindowCounter(
                    totalPermits = 100,
                    replenishmentPeriod = 1.seconds,
                    queueLength = 100000
                )
            )
            baseTimeoutDuration = ZERO
            callWeight { 1 }
            keyResolver { call -> call.request.uri }
            onRejectedCall { call, retryAfterDuration ->
                // TODO: cancellation of the call will invalidate the response set here
                call.response.header("Retry-After", retryAfterDuration.inWholeSeconds.toString())
                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                call.response.header(HttpHeaders.AccessControlAllowMethods, "*")
                call.response.header(HttpHeaders.AccessControlAllowHeaders, "*")
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    "Rate limit exceeded. Try again in $retryAfterDuration."
                )
            }
            excludeFromRateLimiting {
                it.request.uri == ServerRoutes.UNRATE_LIMITED
            }
            interceptPhase(CallSetup)
        }
        routing {
            get(ServerRoutes.RATE_LIMITED) {
                // this delay is to simulate processing time
                // but also ensures that no more than `totalPermits` requests are processed in the same second
                call.respondText("This is a rate-limited route.")
            }
            get(ServerRoutes.UNRATE_LIMITED) {
                call.respondText("This is an unrate-limited route.")
            }
        }
    }.start(wait = true)
}
