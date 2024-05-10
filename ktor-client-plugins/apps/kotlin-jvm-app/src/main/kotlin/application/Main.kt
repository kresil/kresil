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
import kresil.ktor.plugins.retry.client.KresilRetryPlugin
import kresil.ktor.plugins.retry.client.kRetry

class NetworkError : Exception()

suspend fun main() {
    val serverJob = CoroutineScope(Dispatchers.Default).launch { startUnreliableServer() }
    val client = HttpClient(CIO) {
        install(KresilRetryPlugin) {
            retryOnTimeout()
            retryOnException { it is NetworkError } // should not alter behavior of the plugin
            // constantDelay(2.seconds)
            retryOnServerErrors()
            noDelay()
            maxAttempts = 5
            modifyRequestOnRetry { request, attempt ->
                request.headers.append("GLOBAL_RETRY_COUNT", attempt.toString())
            }
        }
        /*install(HttpTimeout) {
            requestTimeoutMillis = 10
            connectTimeoutMillis = 10
            socketTimeoutMillis = 10
        }*/
        // install(HttpRequestRetry)
    }

    client.post {
        url("http://127.0.0.1:8080/")
        setBody("Hello, Kresil!")
        kRetry {
            modifyRequestOnRetry { builder, attempt ->
                builder.headers.append("REQUEST_RETRY_COUNT", attempt.toString())
            }
        }
    }

    client.close()
    serverJob.cancelAndJoin()
}

suspend fun startUnreliableServer() {
    var requestCount = 0
    embeddedServer(Netty, port = 8080) {
        routing {
            post("/") {
                val text = call.receiveText()
                println("Server received: $text")
                requestCount += 1
                when (requestCount) {
                    in 1..4 -> call.respondText("Server is down", status = HttpStatusCode.InternalServerError)
                    else -> call.respondText("Server is back online!")
                }
            }
        }
    }.start(wait = true)
}
