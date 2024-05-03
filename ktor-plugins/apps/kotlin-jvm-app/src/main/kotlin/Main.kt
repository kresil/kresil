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
import kotlinx.coroutines.launch
import kresil.ktor.plugins.client.KresilRetryPlugin
import kotlin.time.Duration.Companion.seconds

suspend fun main() {
    val serverJob = CoroutineScope(Dispatchers.Default).launch { startSlowServer() }

    val client = HttpClient(CIO) {
        install(KresilRetryPlugin) {
            maxAttempts = 3
            constantDelay(5.seconds)
            retryIf { true }
            retryOnResult { true }
        }
        // install(HttpRequestRetry)
    }

    client.post {
        url("http://127.0.0.1:8080/")
        setBody("Hello, Kresil!")
        /*retry {
            retryOnServerErrors(maxRetries = Int.MAX_VALUE)
            constantDelay(2.seconds.inWholeMilliseconds)
            modifyRequest {
                it.setBody(TextContent("With Different body ...", ContentType.Text.Plain))
            }
        }*/
    }

    client.close()
    serverJob.cancelAndJoin()
}

suspend fun startSlowServer() {
    var requestCount = 0
    embeddedServer(Netty, port = 8080) {
        routing {
            post("/") {
                val text = call.receiveText()
                println("Server received: $text")
                requestCount += 1
                when (requestCount) {
                    in 1..2 -> call.respondText("Server is down", status = HttpStatusCode.InternalServerError)
                    in 3..10 -> call.respondText("Server is back online!")
                    else -> call.respondText("Server is overloaded", status = HttpStatusCode.ServiceUnavailable)
                }
            }
        }
    }.start(wait = true)
}
