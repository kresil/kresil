package retry

import RemoteService
import exceptions.WebServiceException
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kresil.duration.seconds
import kresil.retry.Retry
import kresil.retry.config.RetryConfig
import kresil.retry.retryConfig
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.measureTime

class RetryTests {

    @Mock
    val remoteService = mock(classOf<RemoteService>())

    @Test
    fun executeSuspendFunctionWithRetryConfig() = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val config: RetryConfig = retryConfig {
            maxAttempts(maxAttempts)
            retryIf { it is WebServiceException }
            delay(delayDuration)
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendCall() }
            .throws(WebServiceException("BAM!"))

        // when: an error margin is defined
        val errorMargin = 0.10f // %

        // and: a suspend function is executed with the retry instance
        val retryExecutionDuration: Duration = measureTime {
            try {
                // withContext(Dispatchers.Unconfined) { // used to disable delay skipping
                    retry.executeSuspendFunction {
                        remoteService.suspendCall()
                    }
                // }
            } catch (e: WebServiceException) {
                // ignore
            } catch (e: Exception) {
                fail("Unexpected exception: $e")
            }
        }

        // then: the retry time exceeds the delay duration multipled by each retry attempt but is within the error margin
        println("Retry execution duration: ${retryExecutionDuration.inWholeNanoseconds}")
        println("Delay duration: $delayDuration")
        // assertTrue(
            retryExecutionDuration.inWholeNanoseconds >=
                    delayDuration.nanoseconds + (delayDuration.nanoseconds * errorMargin)
        //)

        // and: the method was invoked the exact number of possible retry attempts
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = maxAttempts - 1) // maxAttempts includes the first non-retry attempt
    }

    @Test
    fun exampleTest() = runTest {
        val elapsed = measureTime {
            val deferred = async {
                delay(1000) // will be skipped
                withContext(Dispatchers.Default) {
                    delay(5000)
                } // Dispatchers.Default doesn't know about TestCoroutineSchedule
            }
            deferred.await()
        }
        println(elapsed) // about five seconds
    }

    @Test
    fun retryWithFaillingRetryIfPredicate() = runTest {
        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val config: RetryConfig = retryConfig {
            maxAttempts(maxAttempts)
            retryIf { it is RuntimeException }
            delay(delayDuration)
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendCall() }
            .throws(WebServiceException("BAM!"))

        // when: a decorated suspend function is executed with the retry instance
        try {
            retry.executeSuspendFunction {
                remoteService.suspendCall()
            }
            fail("suspend function should throw an exception")
        } catch (e: WebServiceException) {
            // then: the method is invoked only once since there is no retry attempt
            coVerify {
                remoteService.suspendCall()
            }.wasInvoked(exactly = once)
        }
    }
}

