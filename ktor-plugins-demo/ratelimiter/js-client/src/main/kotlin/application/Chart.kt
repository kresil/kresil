package application

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.json

external class Chart(
    ctx: dynamic,
    config: dynamic,
) {
    val config: dynamic = definedExternally
    fun update(): dynamic = definedExternally
}

class ChartManager(elementId: String) {
    private val chart = Chart(
        ctx = (document.getElementById(elementId) as HTMLCanvasElement).getContext("2d"),
        config = json(
            "type" to "line",
            "data" to json(
                "labels" to arrayOf<Int>(),
                "datasets" to arrayOf(
                    json(
                        "label" to "Rate Limited Route",
                        "data" to arrayOf<Int>(),
                        "borderColor" to "rgb(255, 99, 132)",
                        "fill" to false,
                        "tension" to 0.1
                    ),
                    json(
                        "label" to "Unrate Limited Route",
                        "data" to arrayOf<Int>(),
                        "borderColor" to "rgb(75, 192, 192)",
                        "fill" to false,
                        "tension" to 0.1
                    ),
                )
            ),
            "options" to json(
                "scales" to json(
                    "x" to json("beginAtZero" to true),
                    "y" to json("beginAtZero" to true)
                )
            )
        )
    )

    fun addDataPoint(currentTimePeriod: Int, rateLimitedRouteRequests: Int, unrateLimitedRouteRequests: Int) {
        chart.config.data.labels.push(currentTimePeriod)
        chart.config.data.datasets[0].data.push(rateLimitedRouteRequests)
        chart.config.data.datasets[1].data.push(unrateLimitedRouteRequests)
        chart.update()
    }

    fun reset() {
        chart.config.data.labels = arrayOf<Int>()
        chart.config.data.datasets[0].data = arrayOf<Int>()
        chart.config.data.datasets[1].data = arrayOf<Int>()
        chart.update()
    }
}
