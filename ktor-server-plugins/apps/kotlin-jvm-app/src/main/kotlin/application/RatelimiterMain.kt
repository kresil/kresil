package application

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kresil.ktor.server.plugins.ratelimiter.KresilRateLimiterPlugin
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val PORT = 8080
private const val BASE = "http://127.0.0.1:$PORT"

object ServerRoutes {
    const val RATE_LIMITED = "/rate-limited"
    const val EXCLUDE = "/exclude"
}

suspend fun main() {
    val serverJob = CoroutineScope(Dispatchers.Default).launch { startRateLimitedServer() }
    val client = HttpClient(CIO)
    val clientScope = CoroutineScope(Dispatchers.Default)
    println("Client: Waiting for server to start...")
    delay(5.seconds)
    println("Client: Starting requests...")
    val requestA = clientScope.launch {
        println("Client: Sending request A...")
        client.get(BASE + ServerRoutes.RATE_LIMITED)
        println("Client: Request A completed.")
    }

    val requestB = clientScope.launch {
        println("Client: Sending request B...")
        client.get(BASE + ServerRoutes.RATE_LIMITED)
        println("Client: Request B completed.")
    }

    val excludedRequest = clientScope.launch {
        println("Client: Sending excluded request...")
        client.get(BASE + ServerRoutes.EXCLUDE)
        println("Client: Excluded request completed.")
    }
    requestA.join(); requestB.join(); excludedRequest.join()
    client.close()
    serverJob.cancelAndJoin()
}

fun startRateLimitedServer() {
    val callWeight = 2 // note: with this setup, the rate-limited endpoints will allow only one request at a time
    embeddedServer(Netty, port = PORT) {
        install(KresilRateLimiterPlugin) {
            algorithm(
                RateLimitingAlgorithm.FixedWindowCounter(
                    totalPermits = callWeight,
                    replenishmentPeriod = 1.minutes,
                    queueLength = 0
                )
            )
            baseTimeoutDuration = 3.seconds
            callWeight { callWeight }
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
                // TODO: improve given information for successful requests
                call.response.header("X-Rate-Limited", "false")
            }
            excludeFromRateLimiting { call -> call.request.uri == ServerRoutes.EXCLUDE }
            interceptPhase(CallSetup)
        }
        configureRouting()
    }.start(wait = true)
}

fun Application.configureRouting() {
    routing {
        get(ServerRoutes.RATE_LIMITED) {
            println("Server: Request received in: ${ServerRoutes.RATE_LIMITED}")
            delay(10.seconds) // to hold the request for a while
            call.respondText("Congratulations! You have passed rate limiting.")
        }
        get(ServerRoutes.EXCLUDE) {
            println("Server: Request received in: ${ServerRoutes.EXCLUDE}")
            call.respondText("This endpoint is excluded from rate limiting.")
        }
    }
}
