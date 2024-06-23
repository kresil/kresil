package kresil.core.slidingwindow

/**
 * Represents a sliding window that can be used to monitor the failure rate of a system.
 * Records results of operations (successes and failures) in a fixed-size window and calculates the failure rate.
 */
interface FailureRateSlidingWindow<T> {

    /**
     * The fixed size of the window.
     */
    val capacity: Int

    /**
     * Records a successful operation. The term "successful" depends on the implementation.
     */
    fun recordSuccess()

    /**
     * Records a failed operation. The term "failed" depends on the implementation.
     */
    fun recordFailure()

    /**
     * Returns the current failure rate of the system.
     */
    fun currentFailureRate(): Double

    /**
     * Clears the window and any underlying state.
     */
    fun clear()
}
