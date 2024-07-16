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
    private val chart: Chart = Chart(
        (document.getElementById(elementId) as HTMLCanvasElement).getContext("2d"),
        config = json(
            "type" to "line",
            "data" to json(
                "labels" to arrayOf<String>(),
                "datasets" to arrayOf(
                    json(
                        "label" to "Response Time (ms)",
                        "data" to arrayOf<Int>(),
                        "borderColor" to "rgb(75, 192, 192)",
                    )
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

    fun addDataPoint(id: String, value: Int) {
        chart.config.data.labels.push(id)
        chart.config.data.datasets[0].data.push(value)
        chart.update()
    }

    fun reset() {
        chart.config.data.labels = arrayOf<String>()
        chart.config.data.datasets[0].data = arrayOf<Int>()
        chart.update()
    }
}
