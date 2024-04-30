package extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Delays the coroutine execution with real time,
 * since the test context (with [kotlinx.coroutines.test.runTest]) uses virtual time to enable delay skipping behavior.
 * @param duration the duration to delay the coroutine. Default is 1 second.
 */
suspend fun delayWithRealTime(duration: Duration = 1.seconds) {
    withContext(Dispatchers.Default) {
        delay(duration) // delay with real time
    }
}
