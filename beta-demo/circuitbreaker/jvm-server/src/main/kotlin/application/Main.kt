package application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(CORS) {
            anyHost()
        }
        routing {

            // state
            var requestCountV1 = 0
            var cycleCountV1 = 'a'
            var requestCountV2 = 0
            var cycleCountV2 = 'a'

            get("/v1") {
                handleRequest(call = call,
                    getRequestCount = { requestCountV1 },
                    incrementRequestCount = { requestCountV1++ },
                    resetRequestCount = { requestCountV1 = 0 },
                    getCycleCount = { cycleCountV1 },
                    incrementCycleCount = { cycleCountV1++ })
            }

            get("/v2") {
                handleRequest(call = call,
                    getRequestCount = { requestCountV2 },
                    incrementRequestCount = { requestCountV2++ },
                    resetRequestCount = { requestCountV2 = 0 },
                    getCycleCount = { cycleCountV2 },
                    incrementCycleCount = { cycleCountV2++ })
            }
        }
    }.start(wait = true)
}

suspend fun handleRequest(
    call: ApplicationCall,
    getRequestCount: () -> Int,
    incrementRequestCount: () -> Unit,
    resetRequestCount: () -> Unit,
    getCycleCount: () -> Char,
    incrementCycleCount: () -> Unit,
) {
    val text = call.receiveText()
    incrementRequestCount()
    println("Server received request nr(${getRequestCount()}-${getCycleCount()}): $text")
    delay(1.seconds) // simulate server processing time
    when (getRequestCount()) {
        in 1..2 -> call.respondText("Server is back online", status = HttpStatusCode.OK)
        in 3..4 -> call.respondText("Server is down", status = HttpStatusCode.InternalServerError)
        else -> {
            delay(4.seconds) // simulate response delay from being overloaded
            call.respondText("Server is overwhelmed", status = HttpStatusCode.ServiceUnavailable)
            if (getRequestCount() >= 6) {
                resetRequestCount()
                incrementCycleCount()
            }
        }
    }
}
