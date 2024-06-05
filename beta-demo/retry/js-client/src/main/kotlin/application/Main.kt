package application

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.util.logging.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kresil.ktor.plugins.retry.client.KresilRetryPlugin
import kresil.ktor.plugins.retry.client.kRetry
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSpanElement
import kotlin.time.Duration.Companion.milliseconds

private val logger = KtorSimpleLogger("retry-client")

private val client = HttpClient {
    install(KresilRetryPlugin) {
        retryOnServerErrors()
        maxAttempts = 3
        noDelay()
    }
}

object ServerPaths {
    const val BASE_URL = "http://localhost:8080"
    const val ERROR = "$BASE_URL/error"
    const val ERROR_CONFIG = "$BASE_URL/error-config"
    const val TEST = "$BASE_URL/test"
}

suspend fun main() {
    val scope = MainScope()
    val errorRateDisplay =
        document.getElementById("current-error-rate") as HTMLSpanElement
    updateErrorRate(errorRateDisplay)
    setErrorRateButtonEventListener(scope, errorRateDisplay)

    launchClientWithRetryPlugin(scope)
    launchClientWithoutRetryPlugin(scope)

}

private fun launchClientWithoutRetryPlugin(scope: CoroutineScope) {
    scope.launch {
        // no retry stats
        val noRetryTotalRequestsDisplayElement =
            document.getElementById("no-retry-total-requests") as HTMLSpanElement
        val noRetryTotalErrorsDisplayElement =
            document.getElementById("no-retry-total-errors") as HTMLSpanElement
        val noRetryErrorRateDisplayElement =
            document.getElementById("no-retry-error-rate") as HTMLSpanElement
        testServer(
            totalRequestsDisplayElement = noRetryTotalRequestsDisplayElement,
            totalErrorsDisplayElement = noRetryTotalErrorsDisplayElement,
            errorRateDisplayElement = noRetryErrorRateDisplayElement,
            useRetry = false
        )
    }
}

private fun launchClientWithRetryPlugin(scope: CoroutineScope) {
    scope.launch {
        // retry stats
        val retryTotalRequestsDisplayElement =
            document.getElementById("retry-total-requests") as HTMLSpanElement
        val retryTotalErrorsDisplayElement =
            document.getElementById("retry-total-errors") as HTMLSpanElement
        val retryErrorRateDisplayElement =
            document.getElementById("retry-error-rate") as HTMLSpanElement
        testServer(
            totalRequestsDisplayElement = retryTotalRequestsDisplayElement,
            totalErrorsDisplayElement = retryTotalErrorsDisplayElement,
            errorRateDisplayElement = retryErrorRateDisplayElement,
            useRetry = true
        )
    }
}

private fun setErrorRateButtonEventListener(scope: CoroutineScope, errorRateDisplay: HTMLSpanElement) {
    val setErrorRateButton =
        document.getElementById("set-error-rate-button") as HTMLButtonElement
    setErrorRateButton.onclick = {
        val errorRateInput =
            document.getElementById("error-rate-input") as HTMLInputElement
        val errorRate = errorRateInput.value
        scope.launch {
            val response: HttpResponse = client.get("${ServerPaths.ERROR_CONFIG}?rate=$errorRate")
            logger.info("Response: $response")
            if (response.status.value == 200) {
                updateErrorRate(errorRateDisplay)
            } else {
                window.alert("Failed to set error rate")
            }
        }
    }
}

private suspend fun updateErrorRate(errorRateDisplay: HTMLSpanElement) {
    val response: HttpResponse = client.get(ServerPaths.ERROR)
    logger.info("Update error rate response: $response")
    if (response.status.value == 200) {
        val errorRate = response.bodyAsText()
        errorRateDisplay.textContent = errorRate
    } else {
        window.alert("Failed to get error rate, server might be down")
    }
}

private suspend fun testServer(
    totalRequestsDisplayElement: HTMLSpanElement,
    totalErrorsDisplayElement: HTMLSpanElement,
    errorRateDisplayElement: HTMLSpanElement,
    useRetry: Boolean,
) {
    var serverErrors = 0
    var totalNrOfRequests = 0
    logger.info("Starting client loop")
    while (true) {
        delay(250.milliseconds)
        totalNrOfRequests++

        var response: HttpResponse? = null
        try {
            val url = ServerPaths.TEST
            response = if (useRetry) {
                client.get(url)
            } else {
                client.get {
                    url(url)
                    kRetry(true)
                }
            }
        } catch (_: Exception) {
        }

        if (response == null || response.status == InternalServerError) {
            serverErrors++
        }

        val clientErrorRate = serverErrors.toDouble() / totalNrOfRequests
        val retryMode = if (useRetry) "with retry" else "without retry"
        totalRequestsDisplayElement.textContent = totalNrOfRequests.toString().also {
            logger.info("Total requests $retryMode: $totalNrOfRequests")
        }
        totalErrorsDisplayElement.textContent = serverErrors.toString().also {
            logger.info("Total errors $retryMode: $serverErrors")
        }
        errorRateDisplayElement.textContent = clientErrorRate.toString().also {
            logger.info("Error rate $retryMode: $clientErrorRate")
        }
    }
}
