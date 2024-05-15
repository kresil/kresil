package kresil.circuitbreaker.slidingwindow

import kresil.core.utils.RingBuffer

internal class CountBasedSlidingWindow(override val capacity: Int) : SlidingWindow<Boolean> {
    private val buffer = RingBuffer<Boolean>(capacity)

    override fun recordSuccess() {
        buffer.add(true)
    }

    override fun recordFailure() {
        buffer.add(false)
    }

    override fun currentFailureRate(): Double {
        return (buffer.count { !it }.toDouble() / capacity)
    }

    override fun clear() {
        buffer.clear()
    }
}
