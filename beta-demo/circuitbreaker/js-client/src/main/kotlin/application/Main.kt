package application

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kresil.circuitbreaker.exceptions.CallNotPermittedException
import kresil.ktor.client.plugins.circuitbreaker.KresilCircuitBreakerPlugin
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLSpanElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

private val logger = KtorSimpleLogger("cbreaker-client")

private val clientWithoutCBreaker = HttpClient()
private val clientWithCBreaker = HttpClient {
    install(KresilCircuitBreakerPlugin) {
        permittedNumberOfCallsInHalfOpenState = 1
        maxWaitDurationInHalfOpenState = Duration.ZERO
        constantDelayInOpenState(500.milliseconds)
        failureRateThreshold = 0.5
        slidingWindow(4, 4)
        recordFailureOnServerErrors()
    }
}

private object ServerPaths {
    const val BASE = "http://127.0.0.1:8080"
    const val V1 = "$BASE/v1"
    const val V2 = "$BASE/v2"
}

fun main() {
    val scope = MainScope()
    launchClientWithCBreakerPlugin(scope)
    launchClientWithoutCBreakerPlugin(scope)
}

private fun launchClientWithoutCBreakerPlugin(scope: CoroutineScope) {
    scope.launch {
        val messagesWithoutCBreaker = document.getElementById("messages-without-circuit-breaker") as HTMLDivElement
        testServer(
            messageElement = messagesWithoutCBreaker,
            usingCBreaker = false,
        )
    }
}

fun launchClientWithCBreakerPlugin(scope: CoroutineScope) {
    scope.launch {
        val messagesWithCBreaker = document.getElementById("messages-with-circuit-breaker") as HTMLDivElement
        testServer(
            messageElement = messagesWithCBreaker,
            usingCBreaker = true,
        )
    }
}

suspend fun testServer(
    messageElement: HTMLDivElement,
    usingCBreaker: Boolean,
) {
    while (true) {
        delay(10.milliseconds)
        try {
            messageElement.appendMessage("\uD83D\uDCE8 Sending request")
            val measuredResponse = measureTimedValue {
                if (usingCBreaker) clientWithCBreaker.get(ServerPaths.V1)
                else clientWithoutCBreaker.get(ServerPaths.V2)
            }
            val (response, duration) = measuredResponse.value to measuredResponse.duration
            val emoji = if (response.status.isSuccess()) "✅" else "❌"
            val message = "$emoji ${response.bodyAsText()} (took ${duration.inWholeSeconds}s)"
            messageElement.appendMessage(message)
        } catch (e: CallNotPermittedException) {
            messageElement.appendMessage("⚡ Circuit Breaker denied call")
        } catch (e: Exception) {
            messageElement.appendMessage("❌ Unexpected error: ${e.message}")
            return
        }
    }
}

fun HTMLDivElement.appendMessage(message: String) {
    val span = document.createElement("span") as HTMLSpanElement
    span.textContent = message
    appendChild(span)
    appendChild(document.createElement("br"))
    span.scrollIntoView()
    logger.info(message)
}
