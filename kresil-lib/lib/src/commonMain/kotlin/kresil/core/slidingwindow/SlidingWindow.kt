package kresil.core.slidingwindow

interface SlidingWindow<T> {
    val capacity: Int
    fun recordSuccess()
    fun recordFailure()
    fun currentFailureRate(): Double
    fun clear()
}
