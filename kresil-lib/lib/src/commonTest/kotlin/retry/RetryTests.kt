package retry

import exceptions.WebServiceException
import extensions.delayWithRealTime
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kresil.retry.Retry
import kresil.retry.RetryEvent
import kresil.retry.builders.retryConfig
import kresil.retry.config.RetryConfig
import kresil.retry.exceptions.MaxRetriesExceededException
import service.ConditionalSuccessRemoteService
import service.RemoteService
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTests {

    @Mock
    val remoteService = mock(classOf<RemoteService>())

    @Test
    fun executeSuspendFunctionWithRetry() = executeSuspendFunctionWithRetry(false)

    @Test
    fun decoratedSuspendFunctionWithRetry() = executeSuspendFunctionWithRetry(true)

    private fun executeSuspendFunctionWithRetry(isDecorated: Boolean) = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts  // includes the first non-retry currentAttempt
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
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

        // and: the amount of time that exceeds the delay duration * retry attempts is advanced
        testScheduler.advanceUntilIdle()

        // then: the retry virtual time equals the delay duration multipled by each retry attempt
        val retryExecutionDuration = currentTime
        val retryAttempts = config.permittedRetryAttempts
        assertEquals(retryExecutionDuration, delayDuration.inWholeMilliseconds * retryAttempts)

        // obs: since runTest is used in this test, the virtual time is used instead of real time as all delays
        // are executed in the test context and consequently skipped to reduce test execution time.
        // This is the reason why it can be equal to the delay duration and not slightly greater.
        // With real time, an error margin would've been needed to consider the test successful
        // (e.g. retryExecutionDuration.inWholeNanoseconds >= delayDuration.inWholeNanoseconds * maxAttempts +
        // (delayDuration.inWholeNanoseconds * 0.01) // 1% error margin)
        // more info at: https://github.com/Kotlin/kotlinx.coroutines/tree/master/kotlinx-coroutines-test

        // and: the method was invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = maxAttempts)

        // and: the retry events are emitted in the correct order
        val retryOnRetryList = List(retryAttempts) { RetryEvent.RetryOnRetry(it + 1) }
        assertEquals(
            listOf(
                *retryOnRetryList.toTypedArray(),
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
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
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

        // and: the amount of time that exceeds the delay duration * retry attempts is advanced
        testScheduler.advanceUntilIdle()

        // then: no delay is executed since the exception is not the one specified in the retry predicate
        val retryExecutionDuration = currentTime
        assertEquals(0, retryExecutionDuration)

        // and: the method is invoked only once since there is no retry attempt
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = once)

        // and: the retry events are emitted in the correct order
        assertEquals<List<RetryEvent>>(
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
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
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

        // and: the amount of time that exceeds the delay duration * retry attempts is advanced
        testScheduler.advanceUntilIdle()

        // then: the retry events are emitted in the correct order
        assertEquals(
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
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
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
                    println("Executing currentAttempt: $currentAttempt")
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
        // and: should use the default max attempts
        val expectedMaxAttempts = 3
        assertEquals(expectedMaxAttempts, config.maxAttempts)

        // and: should retry any exception
        assertTrue(config.retryIf(Exception()))
        assertTrue(config.retryIf(RuntimeException()))
        assertTrue(config.retryIf(WebServiceException("BAM!")))

        // and: should not retry on any result
        assertFalse(config.retryOnResult(null))

        // and: should use exponential delay with default values
        val initialDelay = 500L.milliseconds
        val multiplier = 2.0
        val initialDelayMillis = initialDelay.inWholeMilliseconds
        (1..config.maxAttempts).forEach { attempt ->
            val nextDurationMillis: Long = (initialDelayMillis * multiplier.pow(attempt)).toLong()
            assertEquals(nextDurationMillis.milliseconds, config.delayStrategy(attempt, null))
        }

        // and: the permitted retry attempts is the max attempts minus one, since the first attempt is not a retry
        assertEquals(expectedMaxAttempts - 1, config.permittedRetryAttempts)
    }

    @Test
    fun cancelRetryExecution() = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 2.seconds
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendCall() }
            .throws(WebServiceException("BAM!"))

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        val retryListenersJob = launch {
            retry.onEvent {
                eventsList.add(it)
            }
        }

        // when: a suspend function is executed with the retry instance
        val job = launch {
            retry.executeSuspendFunction {
                remoteService.suspendCall()
            }
        }

        // and: some time that exceeds the delay duration is advanced (to force a retry attempt but not complete)
        testScheduler.advanceTimeBy(delayDuration + 1.seconds)

        // then: the job is not completed yet and can be cancelled
        job.cancelAndJoin()

        // and: the method is invoked twice before the job is cancelled (first attempt + retry)
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = twice)

        println(eventsList)

        // and: the retry events are emitted in the correct order
        assertEquals(
            listOf(
                RetryEvent.RetryOnRetry(1),
                RetryEvent.RetryOnRetry(2), // since the event is emitted before the delay
                RetryEvent.RetryOnCancellation
            ),
            eventsList
        )

        retryListenersJob.cancelAndJoin() // cancel all listeners
    }

    @Test
    fun retryOnResult() = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val result = null
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
            retryOnResult { it == result }
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that returns some result
        coEvery { remoteService.suspendCall() }
            .returns(result)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        val retryListenersJob = launch {
            retry.onEvent {
                eventsList.add(it)
            }
        }

        delayWithRealTime() // wait for listeners to be registered using real time

        launch {
            try {
                // when: a decorated suspend function is executed with the retry instance
                retry.executeSuspendFunction {
                    remoteService.suspendCall()
                }
            } catch (e: MaxRetriesExceededException) {
                // expected
                println("MaxRetriesExceededException: $e")
            } catch (e: Exception) {
                fail("Unexpected exception: $e")
            }
        }

        // and: the amount of time that exceeds the delay duration * retry attempts is advanced
        testScheduler.advanceUntilIdle()

        // then: the retry events are emitted in the correct order
        val retryAttempts = config.permittedRetryAttempts // the first attempt is not a retry
        val retryOnRetryList = List(retryAttempts) { RetryEvent.RetryOnRetry(it + 1) }
        assertEquals(listOf(*retryOnRetryList.toTypedArray()), eventsList.take(retryAttempts))
        eventsList.last().let {
            assertIs<RetryEvent.RetryOnError>(it)
            assertIs<MaxRetriesExceededException>(it.throwable)
        }

        retryListenersJob.cancelAndJoin() // cancel all listeners
    }

    @Test
    fun retryWithConstantDelay() = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendCall() }
            .throws(WebServiceException("BAM!"))

        launch {
            try {
                // when: a decorated suspend function is executed with the retry instance
                retry.executeSuspendFunction {
                    remoteService.suspendCall()
                }
            } catch (e: WebServiceException) {
                // expected
            } catch (e: Exception) {
                fail("Unexpected exception: $e")
            }
        }

        // and: the amount of time that exceeds the delay duration * retry attempts is advanced
        testScheduler.advanceUntilIdle()

        // then: the retry virtual time equals the delay duration multipled by each retry attempt
        val retryExecutionDuration = currentTime
        val retryAttempts = config.permittedRetryAttempts
        assertEquals(retryExecutionDuration, delayDuration.inWholeMilliseconds * retryAttempts)
    }

    @Test
    fun retryWithInvalidConstantDelay() {

        // given: a retry configuration with an invalid constant delay
        val delayDuration = (-2).seconds

        // when: the constant delay is created
        // then: an exception is thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            retryConfig {
                constantDelay(delayDuration)
            }
        }

        // and: the exception message is correct
        assertEquals(
            "Delay duration must be greater than 0",
            exception.message
        )
    }

    @Test
    fun retryWithExponentialDelay() = runTest {

        // given: a retry configuration
        val maxAttempts = 6
        val initialDelay = 1.seconds
        val multiplier = 2.0
        val maxDelay = 10.seconds
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            exponentialDelay(initialDelay, multiplier, maxDelay) // will be: [1s, 2s, 4s, 8s, 10s, 10s]
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendCall() }
            .throws(WebServiceException("BAM!"))

        launch {
            try {
                // when: a decorated suspend function is executed with the retry instance
                retry.executeSuspendFunction {
                    remoteService.suspendCall()
                }
            } catch (e: WebServiceException) {
                // expected
            } catch (e: Exception) {
                fail("Unexpected exception: $e")
            }
        }

        // and: the amount of time that exceeds the delay duration * retry attempts is advanced
        testScheduler.advanceUntilIdle()

        // then: the retry virtual time equals the expected exponential delay
        val retryExecutionDuration = currentTime
        val retryAttempts = config.permittedRetryAttempts
        val expectedDuration = (1..retryAttempts).sumOf { attempt ->
            val initialDelayMillis = initialDelay.inWholeMilliseconds
            val maxDelayMillis = maxDelay.inWholeMilliseconds
            val nextDurationMillis: Long = (initialDelayMillis * multiplier.pow(attempt)).toLong()
            val result = nextDurationMillis.coerceAtMost(maxDelayMillis)
            result
        }
        assertEquals(retryExecutionDuration, expectedDuration)
    }

    @Test
    fun exponentialDelayInvalidMultiplier() {

        // given: a retry configuration with an invalid multiplier
        val multiplier = 1.0 // should be greater than 1

        // when: the exponential delay is created
        // then: an exception is thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            retryConfig {
                exponentialDelay(multiplier = multiplier)
            }
        }

        // and: the exception message is correct
        assertEquals(
            "Multiplier must be greater than 1",
            exception.message
        )
    }

    @Test
    fun exponentialDelayWithInvalidInitialDelay() {

        // given: a retry configuration with an invalid initial delay
        val initialDelay = (-2).seconds

        // when: the exponential delay is created
        // then: an exception is thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            retryConfig {
                exponentialDelay(initialDelay = initialDelay)
            }
        }

        // and: the exception message is correct
        assertEquals(
            "Initial delay duration must be greater than 0",
            exception.message
        )
    }

    @Test
    fun exponentialDelayWithInitialDelayGreaterThanMaxDelay() {

        // given: a retry configuration with an invalid initial delay
        val initialDelay = 10.seconds
        val maxDelay = 5.seconds

        // when: the exponential delay is created
        // then: an exception is thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            retryConfig {
                exponentialDelay(initialDelay = initialDelay, maxDelay = maxDelay)
            }
        }

        // and: the exception message is correct
        assertEquals(
            "Max delay must be greater than initial delay",
            exception.message
        )
    }

    @Test
    fun retryWithCustomDelay() = runTest {

        // given: collectors for the retry attempt, last throwable, and delay durations
        val attemptCollector = mutableListOf<Int>()
        val lastThrowableCollector = mutableListOf<Throwable?>()
        val delayDurationsCollector = mutableListOf<Duration>()

        // and: a retry configuration
        val maxAttempts = 4
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException || it is RuntimeException }
            // simulates delay on each retry attempt: [3s, 1s, 2s]
            // since maxAttempts is 4 and the first attempt is not a retry
            customDelay { attempt, lastThrowable ->
                (if (attempt % 2 == 0) 1.seconds
                else if (lastThrowable is WebServiceException) 2.seconds
                else 3.seconds).also {
                    attemptCollector.add(attempt)
                    lastThrowableCollector.add(lastThrowable)
                    delayDurationsCollector.add(it)
                }
            }
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception that is not always the same
        val unlaughableException = RuntimeException("*you hear giggles in the stacktrace*")
        val webServiceException = WebServiceException("BAM!")
        coEvery { remoteService.suspendCall() }
            .throwsMany( // throws the exceptions in the order they are defined in each call
                unlaughableException,
                unlaughableException,
                webServiceException
            )

        launch {
            try {
                // when: a decorated suspend function is executed with the retry instance
                retry.executeSuspendFunction {
                    remoteService.suspendCall()
                }
            } catch (e: WebServiceException) {
                // expected
            } catch (e: RuntimeException) {
                // expected
            } catch (e: Exception) {
                fail("Unexpected exception: $e")
            }
        }

        // and: the amount of time that exceeds the delay duration * retry attempts is advanced
        testScheduler.advanceUntilIdle()

        // then: the collectors contain the expected values
        assertEquals(listOf(1, 2, 3), attemptCollector)
        assertEquals(
            listOf<Throwable?>(unlaughableException, unlaughableException, webServiceException),
            lastThrowableCollector
        )
        val expectedDelayDurationsList = listOf(3.seconds, 1.seconds, 2.seconds)
        assertEquals(expectedDelayDurationsList, delayDurationsCollector)

        // and: the retry virtual time equals the custom delay
        val retryExecutionDuration = currentTime
        val expectedDuration = expectedDelayDurationsList.sumOf { it.inWholeMilliseconds }
        assertEquals(expectedDuration, retryExecutionDuration)

        // and: the method is invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = maxAttempts)
    }

    @Test
    fun retryOnResultWithCustomDelaySeeLastThrowableHasNull() = runTest {

        // given: collectors for the retry attempt and last throwable
        val attemptCollector = mutableListOf<Int>()
        val lastThrowableCollector = mutableListOf<Throwable?>()

        // given: a retry configuration
        val maxAttempts = 3
        val result = null
        val constantDelay = 3.seconds
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            customDelay { attempt, lastThrowable ->
                constantDelay.also {
                    attemptCollector.add(attempt)
                    lastThrowableCollector.add(lastThrowable)
                }
            }
            retryOnResult { it == result }
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that returns some result
        coEvery { remoteService.suspendCall() }
            .returns(result)

        launch {
            try {
                // when: a decorated suspend function is executed with the retry instance
                retry.executeSuspendFunction {
                    remoteService.suspendCall()
                }
            } catch (e: MaxRetriesExceededException) {
                // expected
                println("MaxRetriesExceededException: $e")
            } catch (e: Exception) {
                fail("Unexpected exception: $e")
            }
        }

        // and: the amount of time that exceeds the delay duration * retry attempts is advanced
        testScheduler.advanceUntilIdle()

        // then: the collectors contain the expected values
        assertEquals(listOf(1, 2), attemptCollector)
        assertEquals(listOf<Throwable?>(null, null), lastThrowableCollector)

        // and: the method is invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = maxAttempts)
    }

    @Test
    fun retryWithNoDelay() = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            noDelay()
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendCall() }
            .throws(WebServiceException("BAM!"))

        launch {
            try {
                // when: a decorated suspend function is executed with the retry instance
                retry.executeSuspendFunction {
                    remoteService.suspendCall()
                }
            } catch (e: WebServiceException) {
                // expected
            } catch (e: Exception) {
                fail("Unexpected exception: $e")
            }
        }

        // and: the amount of time that exceeds the delay duration * retry attempts is advanced
        testScheduler.advanceUntilIdle()

        // then: the retry virtual time equals 0 since there is no delay
        val retryExecutionDuration = currentTime
        assertEquals(0, retryExecutionDuration)

        // and: the method is invoked the amount of times specified in the retry configuration
        coVerify {
            remoteService.suspendCall()
        }.wasInvoked(exactly = maxAttempts)
    }
}
