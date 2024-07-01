package kresil.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Delays the coroutine execution with real time,
 * since the test coroutine scheduler (with [kotlinx.coroutines.test.runTest])
 * uses virtual time to enable delay skipping behaviour (i.e., to fast-forward test execution as much as possible).
 * @param duration the duration to delay the coroutine. Default is 1 second.
 */
suspend fun delayWithRealTime(duration: Duration = 1.seconds) {
    withContext(Dispatchers.Default) {
        delay(duration) // delay with real time
    }
}

/**
 * Measures the time taken to execute the specified [block] with real time, since the test coroutine scheduler
 * (with [kotlinx.coroutines.test.runTest]) uses virtual time to enable delay skipping behaviour
 * (i.e., to fast-forward test execution as much as possible).
 * @param block the block to measure the time taken.
 */
suspend inline fun measureWithRealTime(crossinline block: suspend () -> Unit): Duration {
    return withContext(Dispatchers.Default) {
        measureTime { block() }
    }
}

/**
 * Retrieves an element from the list at the specified [index] with a short delay.
 */
suspend fun <T> MutableList<T>.getWithDelay(index: Int): T {
    delayWithRealTime(100.milliseconds)
    return get(index)
}

/**
 * Generates a random number between this [Int] and [end] (inclusive).
 */
infix fun Int.randomTo(end: Int) = (this..end).random()

/**
 * Generates a random number between this [Duration] and [end] (inclusive).
 */
infix fun Duration.randomTo(end: Duration): Duration = (this.inWholeMilliseconds..end.inWholeMilliseconds).random().milliseconds
