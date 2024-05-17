package kresil.circuitbreaker.slidingwindow

import kresil.core.slidingwindow.SlidingWindow
import kresil.core.utils.RingBuffer

internal class CountBasedSlidingWindow(
    override val capacity: Int,
    private val minimumThroughput: Int,
) : SlidingWindow<Boolean> {

    // state
    private var records: Long = 0 // used to keep track of accumulated records

    init {
        require(capacity > 0) { "Capacity must be greater than 0" }
        require(minimumThroughput > 0) { "Minimum throughput must be greater than 0" }
    }

    private val buffer = RingBuffer<Boolean>(capacity)

    override fun recordSuccess() {
        recordResult(true)
    }

    override fun recordFailure() {
        recordResult(false)
    }

    private fun recordResult(result: Boolean) {
        buffer.add(result); records++
    }

    override fun currentFailureRate(): Double {
        // to decide whether the failure rate is significant enough to be considered
        // given the minimum throughput, we need to check if the number of records,
        // since the last reset, is less than the minimum throughput (and not the capacity)
        return if (records < minimumThroughput) {
            0.0
        } else {
            val failuresCount = buffer.count { !it }
            failuresCount.toDouble() / buffer.size
        }
    }

    fun toList(): List<Boolean?> = buffer.toList()

    override fun clear() {
        records = 0
        buffer.clear()
    }
}
