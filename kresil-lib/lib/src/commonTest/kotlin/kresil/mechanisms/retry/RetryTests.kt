package kresil.mechanisms.retry

import kresil.extensions.delayWithRealTime
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kresil.core.callbacks.OnExceptionPredicate
import kresil.exceptions.WebServiceException
import kresil.retry.Retry
import kresil.retry.config.RetryConfig
import kresil.retry.config.retryConfig
import kresil.retry.delay.RetryCtxDelayProvider
import kresil.retry.delay.RetryDelayStrategy
import kresil.retry.delay.RetryDelayStrategyContext
import kresil.retry.event.RetryEvent
import kresil.retry.exceptions.MaxRetriesExceededException
import kresil.service.RemoteService
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
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
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
            this.maxAttempts = maxAttempts  // includes the first non-retry attempt
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        val remoteServiceException = WebServiceException("BAM!")
        coEvery { remoteService.suspendSupplier() }
            .throws(remoteServiceException)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventsList.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        try {
            when (isDecorated) {
                true -> {
                    // and: a decorated supplier
                    val decorated = retry.decorateSupplier {
                        remoteService.suspendSupplier()
                    }

                    // when: a function is executed with the retry instance [1]
                    decorated()
                }

                false -> {
                    // when: a function is executed with the retry instance [2]
                    retry.executeSupplier {
                        remoteService.suspendSupplier()
                    }
                }
            }
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the retry virtual time equals the delay duration multipled by each retry attempt
        val retryExecutionDuration = currentTime
        val retryAttempts = config.permittedRetryAttempts
        assertEquals(retryExecutionDuration, delayDuration.inWholeMilliseconds * retryAttempts)

        // obs: since runTest is used in this test, the virtual time is used instead of real time as all delays
        // are executed in the test context and consequently skipped to reduce test execution time.
        // This is the reason why it can be equal to the delay duration and not slightly greater.
        // With real time, an error margin would've been needed to consider the test successful
        // (e.g., retryExecutionDuration.inWholeNanoseconds >= delayDuration.inWholeNanoseconds * maxAttempts +
        // (delayDuration.inWholeNanoseconds * 0.01) // 1% error margin)
        // more info at: https://github.com/Kotlin/kotlinx.coroutines/tree/master/kotlinx-coroutines-test

        // and: the method was invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendSupplier()
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

        retry.cancelListeners() // cancel all listeners
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
        coEvery { remoteService.suspendSupplier() }
            .throws(remoteServiceException)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventsList.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: RuntimeException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: no delay is executed since the exception is not the one specified in the retry predicate
        val retryExecutionDuration = currentTime
        assertEquals(0, retryExecutionDuration)

        // and: the method is invoked only once since there is no retry attempt
        coVerify {
            remoteService.suspendSupplier()
        }.wasInvoked(exactly = once)

        // and: the retry events are emitted in the correct order
        assertEquals<List<RetryEvent>>(
            listOf(
                RetryEvent.RetryOnIgnoredError(remoteServiceException)
            ),
            eventsList
        )
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
            kresil.service.ConditionalSuccessRemoteService(
                succeedAfterAttempt = 1,
                throwable = WebServiceException("BAM!")
            )

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventsList.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                conditionalSuccessRemoteService.suspendSupplier()
            }
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the retry events are emitted in the correct order
        assertEquals(
            listOf(
                RetryEvent.RetryOnRetry(1),
                RetryEvent.RetryOnSuccess,
            ),
            eventsList
        )

        retry.cancelListeners() // cancel all listeners
    }

    @Test
    fun operationSuccessWithoutRetry() = runTest {

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

        // and: a remote service that always returns a result
        val result = "Success!"
        coEvery { remoteService.suspendSupplier() }
            .returns(result)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventsList.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        // when: a decorated supplier is executed with the retry instance
        retry.executeSupplier {
            remoteService.suspendSupplier()
        }

        // then: no retry events are emitted
        assertEquals(emptyList(), eventsList)

        // and: the method is invoked only once since there is no retry attempt
        coVerify {
            remoteService.suspendSupplier()
        }.wasInvoked(exactly = once)

        retry.cancelListeners() // cancel all listeners
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
        coEvery { remoteService.suspendSupplier() }
            .throws(WebServiceException("BAM!"))

        // and: event listeners are registered
        retry.onRetry { currentAttempt ->
            println("Executing attempt: $currentAttempt")
        }
        retry.onError { throwable ->
            println("RetryOnError: $throwable")
        }
        retry.onIgnoredError { throwable ->
            println("Ignored error: $throwable")
        }
        retry.onSuccess {
            println("RetryOnSuccess")
        }
        retry.onEvent {
            println("State change: $it")
        }
        // wait for listeners to be registered using real time
        delayWithRealTime()

        try {
            // when: a suspend function is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: event listeners are invoked (see logs)

        retry.cancelListeners() // cancel all listeners

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
        assertTrue(config.retryPredicate(Exception()))
        assertTrue(config.retryPredicate(RuntimeException()))
        assertTrue(config.retryPredicate(WebServiceException("BAM!")))

        // and: should not retry on any result
        assertFalse(config.retryOnResultPredicate(null))

        // and: should use exponential delay with default values
        val initialDelay = 500L.milliseconds
        val multiplier = 2.0
        val initialDelayMillis = initialDelay.inWholeMilliseconds
        (1..config.maxAttempts).forEach { attempt ->
            val nextDurationMillis: Long = (initialDelayMillis * multiplier.pow(attempt - 1)).toLong()
            assertEquals(nextDurationMillis.milliseconds, config.delayStrategy(attempt, RetryDelayStrategyContext()))
        }

        // and: the permitted retry attempts is the max attempts minus one, since the first attempt is not a retry
        assertEquals(expectedMaxAttempts - 1, config.permittedRetryAttempts)
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
        coEvery { remoteService.suspendSupplier() }
            .returns(result)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventsList.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: MaxRetriesExceededException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the retry events are emitted in the correct order
        val retryAttempts = config.permittedRetryAttempts // the first attempt is not a retry
        val retryOnRetryList = List(retryAttempts) { RetryEvent.RetryOnRetry(it + 1) }
        assertEquals(listOf(*retryOnRetryList.toTypedArray()), eventsList.take(retryAttempts))
        eventsList.last().let {
            assertIs<RetryEvent.RetryOnError>(it)
            assertIs<MaxRetriesExceededException>(it.throwable)
        }

        retry.cancelListeners() // cancel all listeners
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
        coEvery { remoteService.suspendSupplier() }
            .throws(WebServiceException("BAM!"))

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

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
        assertFailsWith<IllegalArgumentException> {
            retryConfig {
                constantDelay(delayDuration)
            }
        }
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
        coEvery { remoteService.suspendSupplier() }
            .throws(WebServiceException("BAM!"))

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the retry virtual time equals the expected exponential delay
        val retryExecutionDuration = currentTime
        val retryAttempts = config.permittedRetryAttempts
        val expectedDuration = (1..retryAttempts).sumOf { attempt ->
            val initialDelayMillis = initialDelay.inWholeMilliseconds
            val maxDelayMillis = maxDelay.inWholeMilliseconds
            val nextDurationMillis: Long = (initialDelayMillis * multiplier.pow(attempt - 1)).toLong()
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
        assertFailsWith<IllegalArgumentException> {
            retryConfig {
                exponentialDelay(multiplier = multiplier)
            }
        }

    }

    @Test
    fun exponentialDelayWithInvalidInitialDelay() {

        // given: a retry configuration with an invalid initial delay
        val initialDelay = (-2).seconds

        // when: the exponential delay is created
        // then: an exception is thrown
        assertFailsWith<IllegalArgumentException> {
            retryConfig {
                exponentialDelay(initialDelay = initialDelay)
            }
        }
    }

    @Test
    fun exponentialDelayWithInitialDelayGreaterThanMaxDelay() {

        // given: a retry configuration with an invalid initial delay
        val initialDelay = 10.seconds
        val maxDelay = 5.seconds

        // when: the exponential delay is created
        // then: an exception is thrown
        assertFailsWith<IllegalArgumentException> {
            retryConfig {
                exponentialDelay(initialDelay = initialDelay, maxDelay = maxDelay)
            }
        }
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
            customDelay { attempt, context ->
                val lastThrowable = context.lastThrowable
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
        coEvery { remoteService.suspendSupplier() }
            .throwsMany( // throws the exceptions in the order they are defined in each call
                unlaughableException,
                unlaughableException,
                webServiceException
            )


        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: RuntimeException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

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
            remoteService.suspendSupplier()
        }.wasInvoked(exactly = maxAttempts)
    }

    @Test
    fun retryOnResultWithCustomDelaySeeLastThrowableHasNull() = runTest {

        // given: collectors for the retry attempt and last throwable
        val attemptCollector = mutableListOf<Int>()
        val lastThrowableCollector = mutableListOf<Throwable?>()

        // and: a retry configuration
        val maxAttempts = 3
        val result = null
        val constantDelay = 3.seconds
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            customDelay { attempt, context ->
                constantDelay.also {
                    attemptCollector.add(attempt)
                    lastThrowableCollector.add(context.lastThrowable)
                }
            }
            retryOnResult { it == result }
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that returns some result
        coEvery { remoteService.suspendSupplier() }
            .returns(result)

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: MaxRetriesExceededException) {
            // expected
            println("MaxRetriesExceededException: $e")
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the collectors contain the expected values
        assertEquals(listOf(1, 2), attemptCollector)
        assertEquals(listOf<Throwable?>(null, null), lastThrowableCollector)

        // and: the method is invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendSupplier()
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
        coEvery { remoteService.suspendSupplier() }
            .throws(WebServiceException("BAM!"))

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the retry virtual time equals 0 since there is no delay
        val retryExecutionDuration = currentTime
        assertEquals(0, retryExecutionDuration)

        // and: the method is invoked the number of times specified in the retry configuration
        coVerify {
            remoteService.suspendSupplier()
        }.wasInvoked(exactly = maxAttempts)
    }

    @Test
    fun retryWithStatefulCustomDelayProvider() = runTest {

        // given: a stateful custom delay provider
        val statefulDelayProvider = object : RetryCtxDelayProvider {
            var delayProviderRetryCounter = 0
                private set

            override suspend fun delay(attempt: Int, context: RetryDelayStrategyContext): Duration {
                val nextDuration = when {
                    ++delayProviderRetryCounter % 2 == 0 -> 1.seconds
                    else -> 2.seconds
                }
                println("Retry Attempt: $attempt, Delay duration: $nextDuration")
                delayWithRealTime(nextDuration)
                return Duration.ZERO // to skip the default delay provider
            }
        }

        // and: a retry configuration
        val maxAttempts = 3
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            customDelayProvider(statefulDelayProvider)
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendSupplier() }
            .throws(WebServiceException("BAM!"))

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the delay provider is invoked the expected number of times
        assertEquals(retry.config.permittedRetryAttempts, statefulDelayProvider.delayProviderRetryCounter)

        // and: the method is invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendSupplier()
        }.wasInvoked(exactly = maxAttempts)
    }

    @Test
    fun retryWithStatelessCustomDelayProvider() = runTest {

        // given: a stateless custom delay provider
        val statelessDelayProvider = RetryCtxDelayProvider { attempt, _ ->
            val nextDuration = when {
                attempt % 2 == 0 -> 1.seconds
                else -> 2.seconds
            }
            println("Retry Attempt: $attempt, Delay duration: $nextDuration")
            delayWithRealTime(nextDuration)
            Duration.ZERO // to skip the default delay provider
        }

        // and: a retry configuration
        val maxAttempts = 3
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            customDelayProvider(statelessDelayProvider)
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendSupplier() }
            .throws(WebServiceException("BAM!"))

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // and: the method is invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendSupplier()
        }.wasInvoked(exactly = maxAttempts)
    }

    @Test
    fun retryWithStatelessCustomDelayProviderWithNoExternalDelayShouldMimicCustomDelayBehaviour() = runTest {

        // given: collectors for the retry attempt, last throwable, and delay durations
        val attemptCollector = mutableListOf<Int>()
        val lastThrowableCollector = mutableListOf<Throwable?>()
        val delayDurationsCollector = mutableListOf<Duration>()

        // and: a retry configuration with custom delay
        val maxAttempts = 4
        val retryPredicate: OnExceptionPredicate = { it is WebServiceException || it is RuntimeException }
        val customDelayStrategy: RetryDelayStrategy = { attempt, context ->
            val lastThrowable = context.lastThrowable
            (if (attempt % 2 == 0) 1.seconds
            else if (lastThrowable is WebServiceException) 2.seconds
            else 3.seconds).also {
                attemptCollector.add(attempt)
                lastThrowableCollector.add(lastThrowable)
                delayDurationsCollector.add(it)
            }
        }
        val configWithCustomDelay = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf(retryPredicate)
            customDelay(customDelayStrategy)
        }

        // and: a retry configuration with custom delay provider (stateless and with no external delay function)
        val configWithStatelessCustomDelayProvider = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf(retryPredicate)
            customDelayProvider(customDelayStrategy)
        }

        // when: both retry configurations are tested
        val configList = listOf(configWithCustomDelay, configWithStatelessCustomDelayProvider)
        configList.forEachIndexed { index, config ->

            // setups
            if (index > 0) { // reset lists for subsequent configuration
                attemptCollector.clear()
                lastThrowableCollector.clear()
                delayDurationsCollector.clear()
            }

            // and: a retry instance is created
            val retry = Retry(config)

            // and: a remote service that always throws an exception that is not always the same
            val unlaughableException = RuntimeException("*you hear giggles in the stacktrace*")
            val webServiceException = WebServiceException("BAM!")
            coEvery { remoteService.suspendSupplier() }
                .throwsMany( // throws the exceptions in the order they are defined in each call
                    unlaughableException,
                    unlaughableException,
                    webServiceException
                )

            // and: current time is marked
            val beforeRetryMark = testTimeSource.markNow()

            try {
                // when: a decorated supplier is executed with the retry instance
                retry.executeSupplier {
                    remoteService.suspendSupplier()
                }
                fail("should throw an exception")
            } catch (e: WebServiceException) {
                // expected
            } catch (e: RuntimeException) {
                // expected
            } catch (e: Exception) {
                fail("unexpected exception: $e")
            }

            // then: the collectors contain the expected values
            assertEquals(listOf(1, 2, 3), attemptCollector)
            assertEquals(
                listOf<Throwable?>(unlaughableException, unlaughableException, webServiceException),
                lastThrowableCollector
            )
            val expectedDelayDurationsList = listOf(3.seconds, 1.seconds, 2.seconds)
            assertEquals(expectedDelayDurationsList, delayDurationsCollector)

            // and: the retry virtual time equals the custom delay
            val afterRetryMark = testTimeSource.markNow()
            val retryExecutionDuration = afterRetryMark - beforeRetryMark
            val expectedDuration = expectedDelayDurationsList.sumOf { it.inWholeMilliseconds }
            assertEquals(expectedDuration, retryExecutionDuration.inWholeMilliseconds)

            // and: the method is invoked the exact number of times specified in the retry configuration
            coVerify {
                remoteService.suspendSupplier()
            }.wasInvoked(exactly = maxAttempts)
        }

        // then: both retry configurations are equivalent in behaviour
    }

    @Test
    fun retryListenersCancellationDoesNotCancelUnderlyingScope() = runTest {

        // given: a retry instance
        val retry = Retry()

        // and: a listener
        val eventListeners = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventListeners.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        // and: a remote service that always throws an exception
        val exception = WebServiceException("BAM!")
        coEvery { remoteService.suspendSupplier() }
            .throws(exception)

        // when: a decorated supplier is executed with the retry instance
        val decorated = retry.decorateSupplier {
            remoteService.suspendSupplier()
        }
        try {
            decorated()
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the listeners are invoked
        assertEquals(
            listOf(
                RetryEvent.RetryOnRetry(1),
                RetryEvent.RetryOnRetry(2),
                RetryEvent.RetryOnError(exception)
            ),
            eventListeners
        )

        // when: all listeners are cancelled
        retry.cancelListeners()

        // and: another listener is registered
        eventListeners.clear()
        retry.onEvent {
            eventListeners.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        // and: the decorated supplier is executed again
        try {
            decorated()
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the new listener is invoked
        assertEquals(
            listOf(
                RetryEvent.RetryOnRetry(1),
                RetryEvent.RetryOnRetry(2),
                RetryEvent.RetryOnError(exception)
            ),
            eventListeners
        )

    }

    @Test
    fun retryWithDecoratedFunction() = runTest {

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
        val remoteServiceException = WebServiceException("BAM!")
        val input = "input"
        coEvery { remoteService.suspendFunction(input) }
            .throws(remoteServiceException)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventsList.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        try {
            // when: a decorated function is executed with the retry instance
            val decorated = retry.decorateFunction(block = remoteService::suspendFunction)
            decorated(input)
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the retry virtual time equals the delay duration multipled by each retry attempt
        val retryExecutionDuration = currentTime
        val retryAttempts = config.permittedRetryAttempts
        assertEquals(retryExecutionDuration, delayDuration.inWholeMilliseconds * retryAttempts)

        // and: the method was invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendFunction(input)
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

        retry.cancelListeners() // cancel all listeners
    }

    @Test
    fun retryWithDecoratedBiFunction() = runTest {

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
        val remoteServiceException = WebServiceException("BAM!")
        val input1 = "input1"
        val input2 = "input2"
        coEvery { remoteService.suspendBiFunction(input1, input2) }
            .throws(remoteServiceException)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventsList.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        try {
            // when: a decorated bi-function is executed with the retry instance
            val decorated = retry.decorateBiFunction(block = remoteService::suspendBiFunction)
            decorated(input1, input2)
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the retry virtual time equals the delay duration multipled by each retry attempt
        val retryExecutionDuration = currentTime
        val retryAttempts = config.permittedRetryAttempts
        assertEquals(retryExecutionDuration, delayDuration.inWholeMilliseconds * retryAttempts)

        // and: the method was invoked the exact number of times specified in the retry configuration
        coVerify {
            remoteService.suspendBiFunction(input1, input2)
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

        retry.cancelListeners() // cancel all listeners
    }

    @Test
    fun overrideAConfiguration() = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val baseConfig: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
        }

        // when: the configuration is overridden
        val newMaxAttempts = 5
        val newConfig = retryConfig(baseConfig) {
            this.maxAttempts = newMaxAttempts
        }

        // then: the new configuration is used
        assertEquals(newMaxAttempts, newConfig.maxAttempts)
    }

    @Test
    fun doNotPropagateExceptionOnError() = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
            disableExceptionHandler()
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that throws several exceptions
        val webServiceException = WebServiceException("BAM!")
        coEvery { remoteService.suspendSupplier() }
            .throws(webServiceException)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventsList.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
        } catch (e: WebServiceException) {
            // then: the exception is not propagated
            fail("The exception should not be propagated")
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // and: the retry events are emitted in the correct order
        assertEquals(
            listOf(
                RetryEvent.RetryOnRetry(1),
                RetryEvent.RetryOnRetry(2),
                RetryEvent.RetryOnError(webServiceException)
            ),
            eventsList
        )

        retry.cancelListeners() // cancel all listeners
    }

    @Test
    fun retryOnResultWithCustomExceptionHandler() = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val result = null
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            constantDelay(delayDuration)
            retryOnResult { it == result }
            disableExceptionHandler()
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that returns some result
        coEvery { remoteService.suspendSupplier() }
            .returns(result)

        // and: event listeners are registered
        val eventsList = mutableListOf<RetryEvent>()
        retry.onEvent {
            eventsList.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        try {
            // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
        } catch (e: MaxRetriesExceededException) {
            fail("Should not throw a MaxRetriesExceededException")
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the retry events are emitted in the correct order
        val retryAttempts = config.permittedRetryAttempts // the first attempt is not a retry
        val retryOnRetryList = List(retryAttempts) { RetryEvent.RetryOnRetry(it + 1) }
        assertEquals(listOf(*retryOnRetryList.toTypedArray()), eventsList.take(retryAttempts))
        eventsList.last().let {
            assertIs<RetryEvent.RetryOnError>(it)
            assertIs<MaxRetriesExceededException>(it.throwable)
        }

        retry.cancelListeners() // cancel all listeners
    }

    @Test
    fun retryWithLinearDelayStrategy() = runTest {

        // given: a retry configuration
        val maxAttempts = 3
        val delayDuration = 3.seconds
        val config: RetryConfig = retryConfig {
            this.maxAttempts = maxAttempts
            retryIf { it is WebServiceException }
            linearDelay(delayDuration)
        }

        // and: a retry instance
        val retry = Retry(config)

        // and: a remote service that always throws an exception
        coEvery { remoteService.suspendSupplier() }
            .throws(WebServiceException("BAM!"))

        try {
             // when: a supplier is executed with the retry instance
            retry.executeSupplier {
                remoteService.suspendSupplier()
            }
            fail("should throw an exception")
        } catch (e: WebServiceException) {
            // expected
        } catch (e: Exception) {
            fail("unexpected exception: $e")
        }

        // then: the retry virtual time equals the delay duration multipled by each retry attempt
        val retryExecutionDuration = currentTime
        val retryAttempts = config.permittedRetryAttempts
        assertEquals(retryExecutionDuration, delayDuration.inWholeMilliseconds * retryAttempts + delayDuration.inWholeMilliseconds)
    }

    @Test
    fun linearDelayWithInvalidInitialDelay() {

        // given: a retry configuration with an invalid initial delay
        val initialDelay = (-2).seconds

        // when: the linear delay is created
        // then: an exception is thrown
        assertFailsWith<IllegalArgumentException> {
            retryConfig {
                linearDelay(initialDelay)
            }
        }
    }

    @Test
    fun linearDelayWithInitialDelayGreaterThanMaxDelay() {

        // given: a retry configuration with an invalid initial delay
        val initialDelay = 10.seconds
        val maxDelay = 5.seconds

        // when: the linear delay is created
        // then: an exception is thrown
        assertFailsWith<IllegalArgumentException> {
            retryConfig {
                linearDelay(initialDelay, maxDelay)
            }
        }
    }

}
