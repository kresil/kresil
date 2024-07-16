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
import kotlin.time.Duration.Companion.milliseconds

private const val PORT = 8080

enum class ServerState {
    ONLINE, DOWN
}

fun main() {
    embeddedServer(Netty, port = PORT) {
        install(CORS) {
            anyHost()
        }
        routing {
            var currentState = ServerState.ONLINE

            get("/get-state") {
                println("Received request to get server state")
                handleRequest(call, currentState)
            }
            post("/state") {
                println("Received request to change server state")
                val state = call.receiveText()
                currentState = when (state) {
                    "online" -> ServerState.ONLINE
                    "down" -> ServerState.DOWN
                    else -> throw IllegalArgumentException("Invalid state")
                }
                println("Server state changed to $state")
                call.respondText("Server state changed to $state", status = HttpStatusCode.OK)
            }
        }
    }.start(wait = true)
}

suspend fun handleRequest(call: ApplicationCall, currentState: ServerState) {
    delay(500.milliseconds)
    when (currentState) {
        ServerState.ONLINE -> call.respondText("Server is online", status = HttpStatusCode.OK)
        ServerState.DOWN -> call.respondText("Server is down", status = HttpStatusCode.InternalServerError)
    }
}
