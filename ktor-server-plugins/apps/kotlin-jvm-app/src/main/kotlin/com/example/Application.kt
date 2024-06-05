package com.example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kresil.ktor.server.plugins.CustomHeaderPlugin

suspend fun main() {
    embeddedServer(Netty, port = 8080) {
        install(CustomHeaderPlugin) {
            headerName = "X-Server-Name"
            headerValue = "Kresil"
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
