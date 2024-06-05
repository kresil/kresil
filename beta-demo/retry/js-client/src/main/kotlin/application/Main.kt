package application

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.util.logging.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kresil.ktor.plugins.retry.client.KresilRetryPlugin
import org.w3c.dom.*
import kotlin.time.Duration.Companion.milliseconds

val logger = KtorSimpleLogger("retry-client")

val clientWithRetry = HttpClient {
    install(KresilRetryPlugin) {
        retryOnServerErrors()
        maxAttempts = 3
        noDelay()
    }
}

val scope = MainScope()

suspend fun main() {
    val errorRateDisplay = document.getElementById("current-error-rate") as HTMLSpanElement

    val retryTotalRequestsDisplay = document.getElementById("retry-total-requests") as HTMLSpanElement
    val retryTotalErrorsDisplay = document.getElementById("retry-total-errors") as HTMLSpanElement
    val retryErrorRateDisplay = document.getElementById("retry-error-rate") as HTMLSpanElement

    scope.launch {
        var serverErrorsWithRetry = 0
        var totalNrOfRequestsWithRetry = 0
        logger.info("Starting client loop")
        repeat(1) {
            delay(250.milliseconds)
            totalNrOfRequestsWithRetry++

            var response: HttpResponse? = null
            try {
                response = clientWithRetry.get("http://localhost:8080/test")
            } catch (_: Exception) {}

            if (response == null || response.status == InternalServerError) {
                serverErrorsWithRetry++
            }

            val clientErrorRate = serverErrorsWithRetry.toDouble() / totalNrOfRequestsWithRetry

            logger.info("Total requests with retry: $totalNrOfRequestsWithRetry")
            logger.info("Total errors with retry: $serverErrorsWithRetry")
            logger.info("Error rate with retry: $clientErrorRate")
            retryTotalRequestsDisplay.textContent = totalNrOfRequestsWithRetry.toString()
            retryTotalErrorsDisplay.textContent = serverErrorsWithRetry.toString()
            retryErrorRateDisplay.textContent = clientErrorRate.toString()
        }
    }

    val setErrorRateButton = document.getElementById("set-error-rate-button") as HTMLButtonElement
    setErrorRateButton.onclick = {
        val errorRateInput = document.getElementById("error-rate-input") as HTMLInputElement
        val errorRate = errorRateInput.value
        var failed = false
        scope.launch {
            lateinit var response: HttpResponse
            try {
                response = clientWithRetry.get("http://localhost:8080/error-config?rate=$errorRate")
            } catch (e: Exception) {
                failed = true
            }
            logger.info("Response: $response")
            if (response.status.value != 200 || failed) {
                window.alert("Failed to set error rate")
            } else {
                errorRateDisplay.textContent = errorRate
            }
        }
    }
}
