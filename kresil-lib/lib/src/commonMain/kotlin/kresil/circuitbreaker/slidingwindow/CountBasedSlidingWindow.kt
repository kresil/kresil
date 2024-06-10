package kresil.circuitbreaker.slidingwindow

import kresil.core.slidingwindow.FailureRateSlidingWindow
import kresil.core.utils.RingBuffer

/**
 * A sliding window implementation that uses a count-based approach.
 * To simplify the implementation, the window will only record boolean values,
 * where `true` represents a successful operation and `false` represents a failed operation.
 * @param capacity the fixed size of the window.
 * @param minimumThroughput the minimum number of calls that need to be recorded in the window
 * for the failure rate to be calculated.
 * Even if capacity is reached, the failure rate will not be calculated if the number of calls
 * recorded in the window is less than this value.
 */
internal class CountBasedSlidingWindow(
    override val capacity: Int,
    private val minimumThroughput: Int,
) : FailureRateSlidingWindow<Boolean> {

    // state
    private var records: Long = 0 // used to keep track of accumulated records
    private val buffer = RingBuffer<Boolean>(capacity)

    init {
        require(capacity > 0) { "Capacity must be greater than 0" }
        require(minimumThroughput > 0) { "Minimum throughput must be greater than 0" }
    }

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

    override fun clear() {
        records = 0
        buffer.clear()
    }

}
