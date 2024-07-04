package kresil.mechanisms.ratelimiter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kresil.extensions.delayWithRealTime
import kresil.extensions.randomTo
import kresil.ratelimiter.KeyedRateLimiter
import kresil.ratelimiter.algorithm.RateLimitingAlgorithm.FixedWindowCounter
import kresil.ratelimiter.config.rateLimiterConfig
import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExperimentalCoroutinesApi
class KeyedRateLimiterTests {

    @Test
    fun shouldUseSameRateLimiterForSameKey() = runTest {
        // given: a keyed rate limiter with each rate limiter having a total of 1 permit and a queue length of 0
        val totalPermits = 100 randomTo 1000
        val keyedRateLimiter = KeyedRateLimiter<String>(
            config = rateLimiterConfig {
                algorithm(
                    FixedWindowCounter(
                        totalPermits = totalPermits,
                        refreshPeriod = INFINITE,
                        queueLength = 0
                    )
                )
                baseTimeoutDuration = INFINITE
                onRejected { throw it }
            }
        )

        // when: a call is made with a key
        val keyA = "keyA"
        val request = launch {
            keyedRateLimiter.call(keyA, permits = totalPermits) {
                // assume expensive operation to hold permits indefinitely
                delayWithRealTime(INFINITE)
            }
        }
        delayWithRealTime(250.milliseconds) // wait for the request to be processed

        // then: another call with the same key should fail since all permits are in use and the queue length is 0
        assertFailsWith<RateLimiterRejectedException> {
            keyedRateLimiter.call(keyA, permits = 1) {
                // assume expensive operation
                delayWithRealTime(INFINITE)
            }
        }

        // when: a call with a different key is made while the first request is still in progress
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(3.seconds) {
                val keyB = "keyB"
                keyedRateLimiter.call(keyB, permits = totalPermits) {
                    // no-op
                }
            } ?: fail("Should not timeout")
        }

        // then: it shouldn't suspend indefinitely or throw an error

        // cleanup
        request.cancelAndJoin()

    }

    // TODO: stress test with multiple keys and rate limiters

}
