package application

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
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
import kresil.circuitbreaker.exceptions.CallNotPermittedException
import kresil.ktor.client.plugins.circuitbreaker.KresilCircuitBreakerPlugin
import kotlin.time.Duration.Companion.milliseconds

private const val PORT = 8080

private suspend fun main() {
    val serverJob = CoroutineScope(Dispatchers.Default).launch { startUnreliableServer() }
    val client = HttpClient(CIO) {
        install(KresilCircuitBreakerPlugin) {
            permittedNumberOfCallsInHalfOpenState = 2
            noDelayInOpenState()
            failureRateThreshold = 0.5
            slidingWindow(4, 4)
            recordFailureOnServerErrors()
        }
    }

    repeat(10) {
        delay(250.milliseconds)
        try {
            client.post {
                url("http://127.0.0.1:$PORT/")
                setBody("Hello, Kresil!")
            }
        } catch (e: CallNotPermittedException) {
            println("Client received error: ${e.message}")
        } catch (e: Exception) {
            println("Unexpected error: ${e.message}")
            return@repeat
        }
    }
    client.close()
    serverJob.cancelAndJoin()
}

private suspend fun startUnreliableServer() {
    var requestCount = 0
    embeddedServer(Netty, port = PORT) {
        routing {
            post("/") {
                val text = call.receiveText()
                requestCount += 1
                println("Server received request nr(${requestCount}): $text")
                when (requestCount) {
                    in 1..2 -> call.respondText("Server is back online!")
                    in 3..6 -> call.respondText("Server is down", status = HttpStatusCode.InternalServerError)
                    else -> call.respondText("Server is back online!")
                }
            }
        }
    }.start(wait = true)
}
