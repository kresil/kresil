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
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val PORT = 8080

private suspend fun main() {
    val serverJob = CoroutineScope(Dispatchers.Default).launch { startUnreliableServer() }
    val client = HttpClient(CIO) {
        install(KresilCircuitBreakerPlugin) {
            permittedNumberOfCallsInHalfOpenState = 1
            maxWaitDurationInHalfOpenState = ZERO
            constantDelayInOpenState(500.milliseconds)
            failureRateThreshold = 0.5
            slidingWindow(4, 4)
            recordFailureOnServerErrors()
        }
    }

    repeat(25) {
        delay(250.milliseconds)
        println("Client sending request nr(${it + 1})")
        try {
            client.post {
                url("http://127.0.0.1:$PORT/")
                setBody("Hello, from client!")
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
    var cycleCount = 'a'
    embeddedServer(Netty, port = PORT) {
        routing {
            post("/") {
                val text = call.receiveText()
                requestCount++
                println("Server received request nr(${requestCount}-${cycleCount}): $text")
                delay(1.seconds) // simulate server processing time
                when (requestCount) {
                    in 1..2 -> call.respondText("Server recovered and is back online", status = HttpStatusCode.OK)
                    in 3..4 -> call.respondText("Server is down", status = HttpStatusCode.InternalServerError)
                    else -> {
                        delay(4.seconds) // simulate response delay from being overloaded
                        call.respondText("Server is being overloaded upon restarting", status = HttpStatusCode.ServiceUnavailable)
                        if (requestCount >= 6) {
                            requestCount = 0
                            cycleCount++
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}
