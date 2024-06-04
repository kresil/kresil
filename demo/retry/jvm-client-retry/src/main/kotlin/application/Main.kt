package application

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.util.logging.*
import kotlinx.coroutines.delay
import kresil.ktor.plugins.retry.client.KresilRetryPlugin
import kotlin.time.Duration.Companion.milliseconds

private val logger = KtorSimpleLogger("retry-client")

suspend fun main() {
    val client = HttpClient(CIO) {
        install(KresilRetryPlugin) {
            retryOnServerErrors() // which retries on 5xx errors
            maxAttempts = 3
            noDelay()
        }
    }

    // make several requests and present the server error rate
    var serverErrors = 0
    repeat(Int.MAX_VALUE) {
        val totalNrOfRequests = it + 1
        delay(250.milliseconds)
        var response: HttpResponse? = null
        try {
            response = client.get {
                url("http://localhost:8080/test")
            }
        } catch (_: Exception) {

        }
        if (response == null || response.status == InternalServerError) {
            serverErrors++
        }
        logger.info("Server errors: $serverErrors")
        logger.info("Total number of requests: $totalNrOfRequests")
        val serverErrorRateFromClientPerspective = serverErrors.toDouble() / totalNrOfRequests
        logger.info("Client error rate: $serverErrorRateFromClientPerspective")
    }
    client.close()
}
