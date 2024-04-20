package retry

import exceptions.WebServiceException
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kresil.retry.Retry
import kresil.retry.RetryEvent
import kresil.retry.config.RetryConfig
import kresil.retry.retryConfig
import service.ConditionalSuccessRemoteService
import service.RemoteService
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTests {

    companion object {
        const val ONE_SECOND = 1_000L
    }

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
        val remoteServiceException = WebServiceException("BAM!")
        coEvery { remoteService.suspendCall() }
            .throws(remoteServiceException)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        val retryListenersJob = launch {
            retry.onEvent {
                eventsList.add(it)
            }
        }

        // wait for listeners to be registered using real time
        delayWithRealTime()

        launch {
            try {
                when (isDecorated) {
                    true -> {
                        // and: a decorated suspend function
                        val decorated = retry.decorateSuspendFunction {
                            remoteService.suspendCall()
                        }

                        // when: a suspend function is executed with the retry instance [1]
                        decorated()
                    }

                    false -> {
                        // when: a suspend function is executed with the retry instance [2]
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

        // and: the method was invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = maxAttempts)

        // and: the retry events are emitted in the correct order
        val retryonRetryList = List(retryAttempts) { RetryEvent.RetryOnRetry(it + 1) }
        assertContentEquals(
            listOf(
                *retryonRetryList.toTypedArray(),
                RetryEvent.RetryOnError(remoteServiceException)
            ),
            eventsList
        )

        retryListenersJob.cancelAndJoin() // cancel all listeners
    }

    @Test
    fun retryOnIgnoredError() = runTest {
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
        val remoteServiceException = RuntimeException("Surprise!")
        coEvery { remoteService.suspendCall() }
            .throws(remoteServiceException)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        val retryListenersJob = launch {
            retry.onEvent {
                eventsList.add(it)
            }
        }

        // wait for listeners to be registered using real time
        delayWithRealTime()

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

        // and: the method is invoked only once since there is no retry attempt
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = once)

        // and: the retry events are emitted in the correct order
        assertContentEquals(
            listOf(
                RetryEvent.RetryOnIgnoredError(remoteServiceException)
            ),
            eventsList
        )

        retryListenersJob.cancelAndJoin() // cancel all listeners
    }

    @Test
    fun retryOnRetrySucess() = runTest {
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

        // and: a remote service that throws an exception on the first call
        val conditionalSuccessRemoteService =
            ConditionalSuccessRemoteService(
                succeedAfterAttempt = 1,
                throwable = WebServiceException("BAM!")
            )

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        val retryListenersJob = launch {
            retry.onEvent {
                eventsList.add(it)
            }
        }

        launch {
            try {
                // when: a decorated suspend function is executed with the retry instance
                retry.executeSuspendFunction {
                    conditionalSuccessRemoteService.suspendCall()
                }
            } catch (e: WebServiceException) {
                // expected
            } catch (e: Exception) {
                fail("Unexpected exception: $e")
            }
        }

        testScheduler.advanceUntilIdle() // advance child coroutine to the end

        // then: the retry events are emitted in the correct order
        assertContentEquals(
            listOf(
                RetryEvent.RetryOnRetry(1),
                RetryEvent.RetryOnSuccess,
            ),
            eventsList
        )

        retryListenersJob.cancelAndJoin() // cancel all listeners
    }

    @Test
    fun registerCallbacksInRetryEvents() = runTest {
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

        // and: all retry event listeners launched in parallel
        val retryListenersJob = launch {
            launch {
                retry.onRetry { currentAttempt ->
                    println("Executing attempt: $currentAttempt")
                }
            }
            launch {
                retry.onError { throwable ->
                    println("RetryOnError: $throwable")
                }
            }
            launch {
                retry.onIgnoredError { throwable ->
                    println("Ignored error: $throwable")
                }
            }
            launch {
                retry.onSuccess {
                    println("RetryOnSuccess")
                }
            }
            launch {
                retry.onEvent {
                    println("State change: $it")
                }
            }
        }

        // wait for listeners to be registered using real time
        delayWithRealTime()

        try {
            // when: a suspend function is executed with the retry instance
            retry.executeSuspendFunction {
                remoteService.suspendCall()
            }
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("Unexpected exception: $e")
        }

        // then: event listeners are invoked (see logs)

        retryListenersJob.cancelAndJoin() // cancel all listeners

    }

    @Test
    fun retryWithDefaultConfig() = runTest {
        // given: a retry instance with default configuration
        val retry = Retry()

        // when: config is retrieved
        val config = retry.config

        // then: the default configuration is used
        assertEquals(3, config.maxAttempts)
        assertEquals(500.milliseconds, config.delay)
        assertTrue(config.shouldRetry(Exception())) // should retry any exception
        assertTrue(config.shouldRetry(RuntimeException()))
        assertTrue(config.shouldRetry(WebServiceException("BAM!")))
    }

    /**
     * Delays the coroutine execution with real time,
     * since the test context (with `runTest`) uses virtual time to enable delay skipping behavior.
     */
    private suspend fun delayWithRealTime(millisDuration: Long = ONE_SECOND) {
        withContext(Dispatchers.Default) {
            delay(millisDuration) // delay with real time
        }
    }
}
