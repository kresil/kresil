package application

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kresil.circuitbreaker.exceptions.CallNotPermittedException
import kresil.ktor.client.plugins.circuitbreaker.KresilCircuitBreakerPlugin
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLSpanElement
import kotlin.js.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

private val logger = KtorSimpleLogger("cbreaker-client")

private val client = HttpClient {
    install(KresilCircuitBreakerPlugin) {
        permittedNumberOfCallsInHalfOpenState = 5
        maxWaitDurationInHalfOpenState = Duration.ZERO
        exponentialDelayInOpenState(
            initialDelay = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 10.seconds
        )
        failureRateThreshold = 0.5
        slidingWindow(10, 10)
        recordFailureOnServerErrors()
    }
}

private object ServerPaths {
    const val BASE = "http://127.0.0.1:8080"
    const val GET_STATE = "$BASE/get-state"
    const val POST_STATE = "$BASE/state"
}

private object ServerState {
    const val ONLINE = "online"
    const val DOWN = "down"
}

suspend fun main() {
    val scope = MainScope()

    val chartManager = ChartManager("responseTimeChart")
    val serverStatusSpan = document.getElementById("serverStatus") as HTMLSpanElement
    val onlineButton = document.getElementById("onlineButton") as HTMLButtonElement
    val downButton = document.getElementById("downButton") as HTMLButtonElement
    val resetChartButton = document.getElementById("resetChartButton") as HTMLButtonElement

    onlineButton.onclick = {
        scope.launch {
            setServerState(ServerState.ONLINE, onlineButton, downButton)
            updateServerStatus(ServerState.ONLINE, serverStatusSpan)
        }
    }

    downButton.onclick = {
        scope.launch {
            setServerState(ServerState.DOWN, onlineButton, downButton)
            updateServerStatus(ServerState.DOWN, serverStatusSpan)
        }
    }

    resetChartButton.onclick = {
        chartManager.reset()
    }

    logger.info("Starting client")
    testServer(chartManager)
}

private fun updateServerStatus(
    newStatus: String,
    serverStatusSpan: HTMLSpanElement,
) {
    serverStatusSpan.textContent = newStatus.replaceFirstChar { it.uppercase() }
}

private fun updateServerStateButton(
    state: String,
    onlineButton: HTMLButtonElement,
    downButton: HTMLButtonElement,
) {
    when (state) {
        ServerState.ONLINE -> {
            onlineButton.disabled = true
            downButton.disabled = false
        }
        ServerState.DOWN -> {
            onlineButton.disabled = false
            downButton.disabled = true
        }
    }
}

private suspend fun testServer(chartManager: ChartManager) {
    while (true) {
        val measuredResponse = measureTimedValue {
            logger.info("🚀 Sending request to server")
            try {
                client.get(ServerPaths.GET_STATE)
            } catch (e: CallNotPermittedException) {
                logger.info("⚡ Circuit Breaker denied call")
                null
            } catch (e: Exception) {
                logger.info("❌ Unexpected error: ${e.message}")
                return
            }
        }
        val (response, duration) = measuredResponse.value to measuredResponse.duration
        val durationInMillis = duration.inWholeMilliseconds.toInt()
        chartManager.addDataPoint(Date().toLocaleTimeString(), durationInMillis)
        if (response != null) {
            val emoji = if (response.status == HttpStatusCode.OK) "✅" else "❌"
            val message = "$emoji ${response.bodyAsText()} (took ${durationInMillis}ms)"
            logger.info(message)
        } else {
            logger.info("⚠️ No response received from the server, request took ${durationInMillis}ms")
        }
    }
}

private suspend fun setServerState(
    state: String,
    onlineButton: HTMLButtonElement,
    downButton: HTMLButtonElement,
) {
    try {
        client.post(ServerPaths.POST_STATE) {
            setBody(state)
        }
        logger.info("Server state set to $state")
        updateServerStateButton(state, onlineButton, downButton)
    } catch (e: Exception) {
        logger.info("Failed to set server state: ${e.message}")
    }
}
