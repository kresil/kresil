package application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.random.Random

var errorRate: Double = 0.0

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(CORS) {
            anyHost()
        }
        routing {
            get("/error") {
                call.respond(HttpStatusCode.OK, errorRate.toString())
            }
            get("/error-config") {
                val rate = call.request.queryParameters["rate"]?.toDoubleOrNull()
                if (rate != null && rate in 0.0..1.0) {
                    errorRate = rate
                    call.respond(HttpStatusCode.OK, "Error rate set to $errorRate")
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Invalid error rate. Must be between 0.0 and 1.0.")
                }
            }
            get("/test") {
                if (Random.nextDouble() < errorRate) {
                    call.respond(HttpStatusCode.InternalServerError, "Error")
                } else {
                    call.respond(HttpStatusCode.OK, "Success")
                }
            }
        }
    }.start(wait = true)
}
