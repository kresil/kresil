package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

suspend fun main() {
    embeddedServer(Netty, port = 8080) {
        install(KresilRateLimiterPlugin) {
            algorithm(
                RateLimitingAlgorithm.FixedWindowCounter(
                    totalPermits = 1000,
                    replenishmentPeriod = 1.minutes,
                    queueLength = 0
                )
            )
            baseTimeoutDuration = 3.seconds
            keyResolver { call -> call.request.uri }
            onRejectedCall { call, retryAfterDuration ->
                call.response.header(
                    name = "Retry-After",
                    value = retryAfterDuration.inWholeSeconds.toString()
                )
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = "Rate limit exceeded. Try again in $retryAfterDuration."
                )
            }
            onSuccessCall { call ->
                call.response.header("X-Rate-Limited", "false")
            }
            excludeFromRateLimiting { call -> call.request.uri == "/exclude" }
            interceptPhase(CallSetup)
        }
        routing {
            get("/") {
                call.respondText("Root page")
            }
            post("/") {
                val text = call.receiveText()
                println("Server received: $text")
            }
        }
    }.start(wait = true)
}
