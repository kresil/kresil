package application

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.logging.*
import kotlinx.browser.document
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

private val logger = KtorSimpleLogger("ratelimiter-client")

private object ServerRoutes {
    const val PORT = 8080
    const val BASE = "http://127.0.0.1:$PORT"
    const val RATE_LIMITED = "$BASE/rate-limited"
    const val UNRATE_LIMITED = "$BASE/unrate-limited"
}

suspend fun main() {
    val client = HttpClient(JsClient())
    val scope = MainScope()
    val chartManager = ChartManager("responseTimeChart")
    val resetButton = document.getElementById("resetChartButton") as HTMLButtonElement

    resetButton.onclick = {
        chartManager.reset()
    }

    suspend fun makeSingleRequest(route: String): Boolean {
        val response = client.get(route)
        return response.status.isSuccess()
    }

    val pendingRateLimitedRouteRequests = mutableListOf<Deferred<Boolean>>()
    val pendingUnrateLimitedRouteRequests = mutableListOf<Deferred<Boolean>>()

    // Function to make requests to either rate-limited or unrate-limited routes in parallel
    suspend fun makeRequestsInParallel(route: String, nrOfRequestsToMakeInThisPeriod: Int): Int {
        val deferredResults = (1..nrOfRequestsToMakeInThisPeriod).map {
            scope.async {
                makeSingleRequest(route)
            }
        }
        delay(1.1.seconds) // TODO: needs to be inlined with the rate limiter's replenishment period
        // doesn't need to be a concurrent list because it's only accessed by one coroutine (underlying thread)
        val pendingRequests = when(route) {
            ServerRoutes.RATE_LIMITED -> pendingRateLimitedRouteRequests
            ServerRoutes.UNRATE_LIMITED -> pendingUnrateLimitedRouteRequests
            else -> throw IllegalArgumentException("Invalid route: $route")
        }
        pendingRequests.addAll(deferredResults)
        // count only completed requests and completed successfully,
        // save the other requests for the next period
        var successfulRequests = 0
        for (req in pendingRequests) {
            if (req.isCompleted) {
                pendingRequests.remove(req)
                if (req.await()) { // if it has completed successfully
                    successfulRequests++
                }
            }
        }
        return successfulRequests
    }

    scope.launch {
        var currentTimeUnit = 0
        while(true) {
            val nrOfRequestsToMakeInThisPeriod = (80 - 70 * sin(8 - ((PI * currentTimeUnit++) / 8))).toInt()

            val nonRateLimitedRequests = makeRequestsInParallel(ServerRoutes.UNRATE_LIMITED, nrOfRequestsToMakeInThisPeriod)
            val rateLimitedRequests = makeRequestsInParallel(ServerRoutes.RATE_LIMITED, nrOfRequestsToMakeInThisPeriod)

            chartManager.addDataPoint(currentTimeUnit, rateLimitedRequests, nonRateLimitedRequests)
        }
    }
}
