package kresil.circuitbreaker.slidingwindow

interface SlidingWindow<T> {
    // TODO: failure rate threshold could be calculated based on a smaller number than the capacity
    val capacity: Int
    fun recordSuccess()
    fun recordFailure()
    fun currentFailureRate(): Double
    fun clear()
}
