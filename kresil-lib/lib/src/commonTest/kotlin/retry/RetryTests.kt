package retry

import RemoteService
import exceptions.WebServiceException
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kresil.retry.Retry
import kresil.retry.config.RetryConfig
import kresil.retry.retryConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTests {

    @Mock
    val remoteService = mock(classOf<RemoteService>())

    // TODO: add repeated tests using randomTo (infix) function
    @Test
    fun executeSuspendFunctionWithRetry() = executeSuspendFunctionWithRetry(false)

    @Test
    fun decoratedSuspendFunctionWithRetry() = executeSuspendFunctionWithRetry(true)

    private fun executeSuspendFunctionWithRetry(isDecorated: Boolean) = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val config: RetryConfig = retryConfig {
            maxAttempts(maxAttempts)  // includes the first non-retry attempt
            retryIf { it is WebServiceException }
            delay(delayDuration)
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendCall() }
            .throws(WebServiceException("BAM!"))

        launch {
            try {
                when (isDecorated) {
                    true -> {
                        // and: a decorated suspend function
                        val decorated = retry.decorateSuspendFunction {
                            remoteService.suspendCall()
                        }

                        // when: a suspend function is executed with the retry instance
                        decorated()
                    }

                    false -> {
                        // when: a suspend function is executed with the retry instance
                        retry.executeSuspendFunction {
                            remoteService.suspendCall()
                        }
                    }
                }
                fail("suspend function should throw an exception")
            } catch (e: WebServiceException) {
                // expected
            } catch (e: Exception) {
                fail("unexpected exception: $e")
            }
        }

        testScheduler.advanceUntilIdle() // advance child coroutine to the end

        // then: the retry virtual time equals the delay duration multipled by each retry attempt
        val retryExecutionDuration = currentTime
        val retryAttempts = maxAttempts - 1 // first attempt is not a retry
        assertEquals(retryExecutionDuration, delayDuration.inWholeNanoseconds * retryAttempts)

        // obs: since runTest is used in this test, the virtual time is used instead of real time as all delays
        // are executed in the test context and consequently skipped.
        // This is the reason why it can be equal to the delay duration and not slightly above.
        // With the latter, an error margin would've been needed to consider the test successful
        // (e.g. retryExecutionDuration.inWholeNanoseconds >= delayDuration.inWholeNanoseconds * maxAttempts +
        // (delayDuration.inWholeNanoseconds * 0.01) // 1% error margin)
        // more info at: https://github.com/Kotlin/kotlinx.coroutines/tree/master/kotlinx-coroutines-test

        // and: the method was invoked the exact number of times specified in maxAttempts
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = maxAttempts)
    }

    @Test
    fun retryWithFaillingRetryPredicate() = runTest {
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
            .throws(RuntimeException("Surprise!"))

        launch {
            try {
                // when: a decorated suspend function is executed with the retry instance
                retry.executeSuspendFunction {
                    remoteService.suspendCall()
                }
                fail("suspend function should throw an exception")
            } catch (e: RuntimeException) {
                // expected
            } catch (e: Exception) {
                fail("Unexpected exception: $e")
            }
        }

        testScheduler.advanceUntilIdle() // advance child coroutine to the end

        // then: no delay is executed since the exception is not the one specified in the retry predicate
        val retryExecutionDuration = currentTime
        assertEquals(0, retryExecutionDuration)

        // then: the method is invoked only once since there is no retry attempt
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = once)
    }

}

