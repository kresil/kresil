package kresil.mechanisms.circuitbreaker

import io.mockative.Mock
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.config.circuitBreakerConfig
import kresil.circuitbreaker.event.CircuitBreakerEvent
import kresil.circuitbreaker.exceptions.CallNotPermittedException
import kresil.circuitbreaker.state.CircuitBreakerState
import kresil.exceptions.NetworkException
import kresil.exceptions.WebServiceException
import kresil.extensions.delayWithRealTime
import kresil.service.RemoteService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CircuitBreakerTests {

    @Mock
    val remoteService = mock(classOf<RemoteService>())

    @Test
    fun circuitBreakerShouldOpenWhenFailureRateExceedsThresholdAndMinimumThroughputIsMet() = runTest {
        // given: a circuit breaker configuration
        val nrOfCallsToCalculateFailureRate = 1000
        val config = circuitBreakerConfig {
            this.failureRateThreshold = failureRateThreshold
            slidingWindow(
                size = 2000,
                minimumThroughput = nrOfCallsToCalculateFailureRate
            )
            recordExceptionPredicate { it is WebServiceException }
        }

        // and: a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that always throws an exception
        val remoteServiceException = WebServiceException("BAM!")
        coEvery { remoteService.suspendSupplier() }
            .throws(remoteServiceException)

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(nrOfCallsToCalculateFailureRate) {
            // then: the exception is thrown
            assertFailsWith<WebServiceException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        // when: the remote service is called one more time
        // then: the failure rate exceeds the threshold
        assertFailsWith<CallNotPermittedException> {
            circuitBreaker.executeOperation {
                remoteService.suspendSupplier()
            }
        }

        // and: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())
    }

    @Test
    fun circuitBreakerShouldNotOpenWhenFailureRateIsBelowThreshold() = runTest {
        // given: a circuit breaker configuration
        val nrOfCallsToCalculateFailureRate = 100
        val config = circuitBreakerConfig {
            failureRateThreshold = 0.51
            slidingWindow(
                size = nrOfCallsToCalculateFailureRate,
                minimumThroughput = nrOfCallsToCalculateFailureRate
            )
            recordExceptionPredicate { it is WebServiceException }
        }

        // and: a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that always throws an exception
        val exceptionsToThrow = List(nrOfCallsToCalculateFailureRate) { index ->
            if (index % 2 == 0) NetworkException("Thanks Vodafone!")
            else WebServiceException("BAM!") // note: that only half of the calls are considered failures
        }.toTypedArray()
        coEvery { remoteService.suspendSupplier() }
            .throwsMany(*exceptionsToThrow)

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(nrOfCallsToCalculateFailureRate) { index ->
            if (index % 2 == 0) {
                assertFailsWith<NetworkException> {
                    circuitBreaker.executeOperation {
                        remoteService.suspendSupplier()
                    }
                }
            } else {
                assertFailsWith<WebServiceException> {
                    circuitBreaker.executeOperation {
                        remoteService.suspendSupplier()
                    }
                }
            }
        }

        // then: the circuit breaker should be in the Closed state
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())
    }

    @Test
    fun recordASuccessAsFailureShouldTriggerOpenState() = runTest {
        // given: a circuit breaker configuration
        val result = "success"
        val nrOfCallsToCalculateFailureRate = 10
        val config = circuitBreakerConfig {
            failureRateThreshold = 1.0
            slidingWindow(
                size = nrOfCallsToCalculateFailureRate,
                minimumThroughput = nrOfCallsToCalculateFailureRate
            )
            recordResultPredicate { it == result }
        }

        // and: a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that always returns a result that should be considered a failure
        coEvery { remoteService.suspendSupplier() }
            .returns(result)

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(nrOfCallsToCalculateFailureRate) {
            circuitBreaker.executeOperation<String?> {
                remoteService.suspendSupplier()
            }
        }

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())
    }

    @Test
    fun minimumThroughputDoublesSlidingWindowSize() = runTest {
        // given: a circuit breaker configuration
        val slidingWindowSize = 5
        val minimumThroughput = slidingWindowSize * 2
        val config = circuitBreakerConfig {
            failureRateThreshold = 0.0000000001 // low threshold to trigger the Open state quickly
            slidingWindow(
                size = slidingWindowSize,
                minimumThroughput = minimumThroughput
            )
            recordExceptionPredicate { it is WebServiceException }
        }

        // when: creating a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that throws recorded exceptions as failures until the sliding window is full
        //  but after sliding window is full throws recorded exceptions as successes
        val exceptionsToThrow = List(minimumThroughput) { index ->
            if (index < slidingWindowSize) {
                WebServiceException("BAM!")
            } else {
                NetworkException("Thanks Vodafone!")
            }
        }.toTypedArray()
        coEvery { remoteService.suspendSupplier() }
            .throwsMany(*exceptionsToThrow)

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(minimumThroughput) { index ->
            if (index < slidingWindowSize) {
                assertFailsWith<WebServiceException> {
                    circuitBreaker.executeOperation {
                        remoteService.suspendSupplier()
                    }
                }
            } else {
                assertFailsWith<NetworkException> {
                    circuitBreaker.executeOperation {
                        remoteService.suspendSupplier()
                    }
                }
            }
        }

        // then: the circuit breaker should be in the Closed state,
        //  because the recorded results in the sliding window are now considered successes
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())
    }

    @Test
    fun halfOpenStateShouldTransitionToClosedStateWhenFailureRateIsBelowThreshold() = runTest {
        // given: a circuit breaker configuration
        val minimumThroughput = 10
        val delayInOpenState = 3.seconds
        val config = circuitBreakerConfig {
            failureRateThreshold = 0.5
            slidingWindow(
                size = minimumThroughput,
                minimumThroughput = minimumThroughput
            )
            maxWaitDurationInHalfOpenState = ZERO
            constantDelayInOpenState(delayInOpenState)
            permittedNumberOfCallsInHalfOpenState = 1
            recordExceptionPredicate { it is WebServiceException }
        }

        // and: a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that always throws an exception
        val notAFailure = NetworkException("Thanks Vodafone!")
        val failure = WebServiceException("BAM!")
        val exceptionsToThrow = List(minimumThroughput) { index ->
            if (index % 2 == 0) failure
            else notAFailure // note: that only half of the calls are considered failures
        } + notAFailure // half-open state -> closed state
        coEvery { remoteService.suspendSupplier() }
            .throwsMany(*exceptionsToThrow.toTypedArray())

        // and: functions to register a success or a failure
        suspend fun registerSuccess() {
            assertFailsWith<NetworkException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        suspend fun registerFailure() {
            assertFailsWith<WebServiceException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(minimumThroughput) { index ->
            if (index % 2 == 0) registerFailure()
            else registerSuccess()
        }

        // then: the circuit breaker should be in the Open state
        val openState = circuitBreaker.currentState()
        assertIs<CircuitBreakerState.Open>(openState)
        // and: the values in this state should be correct
        assertEquals(delayInOpenState, openState.delayDuration)
        // when: a call to the remote service is performed
        // then: the call should not be permitted
        assertFailsWith<CallNotPermittedException> {
            circuitBreaker.executeOperation {
                remoteService.suspendSupplier()
            }
        }

        // when: the delay duration has been exceeded
        delayWithRealTime(delayInOpenState)

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState()).apply {
            // and: the values in this state should be correct
            assertEquals(0, nrOfCallsAttempted)
        }

        // when: the remote service is called enough times to make up the permitted number of
        //  calls in the HalfOpen state
        repeat(config.permittedNumberOfCallsInHalfOpenState) {
            registerSuccess()
        }

        // then: the circuit breaker should be in the Closed state because the failure rate is below the threshold
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

    }

    @Test
    fun halfOpenStateShouldTransitionToOpenStateWhenFailureRateExceedsThreshold() = runTest {
        // given: a circuit breaker configuration
        val windowSize = 10
        val initialDelay = 3.seconds
        val config = circuitBreakerConfig {
            failureRateThreshold = 0.5
            slidingWindow(
                size = windowSize,
                minimumThroughput = windowSize
            )
            maxWaitDurationInHalfOpenState = ZERO
            linearDelayInOpenState(initialDelay = initialDelay, multiplier = 1.0)
            permittedNumberOfCallsInHalfOpenState = 3
            recordExceptionPredicate { it is WebServiceException }
        }

        // and: a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that always throws an exception
        val notAFailure = NetworkException("Thanks Vodafone!")
        val failure = WebServiceException("BAM!")
        val exceptionsToThrow = List(windowSize) { index ->
            if (index % 2 == 0) failure
            else notAFailure // note: that only half of the calls are considered failures
        } + listOf( // half-open state -> open state
            notAFailure,
            failure,
            failure
        ) + listOf( // half-open state -> closed state
            notAFailure,
            notAFailure,
            notAFailure
        )
        coEvery { remoteService.suspendSupplier() }
            .throwsMany(*exceptionsToThrow.toTypedArray())

        // and: functions to register a success or a failure
        suspend fun registerSuccess() {
            assertFailsWith<NetworkException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        suspend fun registerFailure() {
            assertFailsWith<WebServiceException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(windowSize) { index ->
            if (index % 2 == 0) registerFailure()
            else registerSuccess()
        }

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState()).apply {
            // and: the values in this state should be correct
            assertEquals(initialDelay, delayDuration)
        }

        // when: a call to the remote service is performed
        // then: the call should not be permitted
        assertFailsWith<CallNotPermittedException> {
            circuitBreaker.executeOperation {
                remoteService.suspendSupplier()
            }
        }

        // when: the delay duration has been exceeded
        delayWithRealTime(initialDelay)

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState())

        // when: the remote service is called enough times to make up the permitted number of
        //  calls in the HalfOpen state
        repeat(config.permittedNumberOfCallsInHalfOpenState) {
            if (it == 0) {
                registerSuccess()
            } else {
                registerFailure()
            }
        }

        // then: the circuit breaker should be in the Open state because the failure rate exceeds the threshold
        val linearDelayInSecondAttempt = initialDelay * 2
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState()).apply {
            // and: the values in this state should be correct
            assertEquals(linearDelayInSecondAttempt, delayDuration)
        }
        // when: a call to the remote service is performed
        // then: the call should not be permitted
        assertFailsWith<CallNotPermittedException> {
            circuitBreaker.executeOperation {
                remoteService.suspendSupplier()
            }
        }

        // when: the delay duration has been exceeded
        delayWithRealTime(linearDelayInSecondAttempt)

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState())

        // when: the remote service is called enough times to make up the permitted number of
        //  calls in the HalfOpen state
        repeat(config.permittedNumberOfCallsInHalfOpenState) {
            registerSuccess()
        }

        // then: the circuit breaker should be in the Closed state because the failure rate is below the threshold
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

    }

    @Test
    fun halfOpenStateShouldTransitionToOpenStateAfterMaxAwaitDurationIsExceeded() = runTest {
        // given: a circuit breaker configuration
        val windowSize = 10
        val delayInOpenState = 1.seconds
        val maxWaitDurationInHalfOpenState = 1.seconds
        val config = circuitBreakerConfig {
            failureRateThreshold = 0.5
            slidingWindow(
                size = windowSize,
                minimumThroughput = windowSize
            )
            this.maxWaitDurationInHalfOpenState = maxWaitDurationInHalfOpenState
            constantDelayInOpenState(delayInOpenState)
            permittedNumberOfCallsInHalfOpenState = 3
            recordExceptionPredicate { it is WebServiceException }
        }

        // and: a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that always throws an exception
        val notAFailure = NetworkException("Thanks Vodafone!")
        val failure = WebServiceException("BAM!")
        val exceptionsToThrow = List(windowSize) { index ->
            if (index % 2 == 0) failure
            else notAFailure // note: that only half of the calls are considered failures
        } + listOf( // half-open state -> open state
            notAFailure,
            failure,
            failure
        )
        coEvery { remoteService.suspendSupplier() }
            .throwsMany(*exceptionsToThrow.toTypedArray())

        // and: functions to register a success or a failure
        suspend fun registerSuccess() {
            assertFailsWith<NetworkException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        suspend fun registerFailure() {
            assertFailsWith<WebServiceException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(windowSize) { index ->
            if (index % 2 == 0) registerFailure()
            else registerSuccess()
        }

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())

        repeat(5) {
            // when: the delay duration has been exceeded
            delayWithRealTime(delayInOpenState)

            // then: the circuit breaker should be in the HalfOpen state
            assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState())

            // when: the max wait duration in the HalfOpen state has been exceeded
            delayWithRealTime(maxWaitDurationInHalfOpenState)

            // then: the circuit breaker should be in the Open state
            assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())
        }
    }

    @Test
    fun slidingWindowShouldNotResetInContinuousCycles() = runTest {
        // given: a circuit breaker configuration
        val minimumThroughput = 10
        val delayInOpenState = 3.seconds
        val config = circuitBreakerConfig {
            failureRateThreshold = 0.5
            slidingWindow(
                size = minimumThroughput,
                minimumThroughput = minimumThroughput
            )
            maxWaitDurationInHalfOpenState = ZERO
            constantDelayInOpenState(delayInOpenState)
            permittedNumberOfCallsInHalfOpenState = 1
            recordExceptionPredicate { it is WebServiceException }
        }

        // and: a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that always throws an exception
        val notAFailure = NetworkException("Thanks Vodafone!")
        val failure = WebServiceException("BAM!")
        val exceptionsToThrow = List(minimumThroughput) { index ->
            if (index % 2 == 0) failure
            else notAFailure // note: that only half of the calls are considered failures
        } + notAFailure + failure
        coEvery { remoteService.suspendSupplier() }
            .throwsMany(*exceptionsToThrow.toTypedArray())

        // and: functions to register a success or a failure
        suspend fun registerSuccess() {
            assertFailsWith<NetworkException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        suspend fun registerFailure() {
            assertFailsWith<WebServiceException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(minimumThroughput) { index ->
            if (index % 2 == 0) registerFailure()
            else registerSuccess()
        }

        // then: the circuit breaker should be in the Open state
        val openState = circuitBreaker.currentState()
        assertIs<CircuitBreakerState.Open>(openState)
        // and: the values in this state should be correct
        assertEquals(delayInOpenState, openState.delayDuration)
        // when: a call to the remote service is performed
        // then: the call should not be permitted
        assertFailsWith<CallNotPermittedException> {
            circuitBreaker.executeOperation {
                remoteService.suspendSupplier()
            }
        }

        // when: the delay duration has been exceeded
        delayWithRealTime(delayInOpenState)

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState())

        // when: the remote service is called enough times to make up the permitted number of
        //  calls in the HalfOpen state
        repeat(config.permittedNumberOfCallsInHalfOpenState) {
            registerSuccess()
        }

        // then: the circuit breaker should be in the Closed state because the failure rate is below the threshold
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // when: the remote service is called again and a failure is recorded
        registerFailure()

        // then: the circuit breaker should be in the Open state,
        //  which means the sliding window did not reset
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())
    }

    @Test
    fun assertCorrectEventEmissionInAFullCycle() = runTest {
        // given: a circuit breaker configuration
        val windowSize = 10
        val initialDelay = 3.seconds
        val config = circuitBreakerConfig {
            failureRateThreshold = 0.5
            slidingWindow(
                size = windowSize,
                minimumThroughput = windowSize
            )
            maxWaitDurationInHalfOpenState = ZERO
            linearDelayInOpenState(initialDelay = initialDelay, multiplier = 1.0)
            permittedNumberOfCallsInHalfOpenState = 3
            recordExceptionPredicate { it is WebServiceException }
        }

        // and: a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that always throws an exception
        val notAFailure = NetworkException("Thanks Vodafone!")
        val failure = WebServiceException("BAM!")
        val exceptionsToThrow = List(windowSize) { index ->
            if (index % 2 == 0) failure
            else notAFailure // note: that only half of the calls are considered failures
        } + listOf( // half-open state -> open state
            notAFailure,
            failure,
            failure
        ) + listOf( // half-open state -> closed state
            notAFailure,
            notAFailure,
            notAFailure
        )
        coEvery { remoteService.suspendSupplier() }
            .throwsMany(*exceptionsToThrow.toTypedArray())

        // and: a listener to record the events
        val events = mutableListOf<CircuitBreakerEvent>()
        circuitBreaker.onEvent {
            events.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        // and: functions to register a success or a failure
        suspend fun registerSuccess() {
            assertFailsWith<NetworkException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        suspend fun registerFailure() {
            assertFailsWith<WebServiceException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(windowSize) { index ->
            if (index % 2 == 0) {
                registerFailure()
            } else {
                registerSuccess()
            }
        }

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())

        // and: the events should be recorded correctly
        var eventIndex = windowSize - 1
        repeat(eventIndex) { index ->
            if (index % 2 == 0) {
                assertIs<CircuitBreakerEvent.RecordedFailure>(events[index]).apply {
                    assertEquals(0.0, failureRate)
                }
            } else {
                assertIs<CircuitBreakerEvent.RecordedSuccess>(events[index]).apply {
                    assertEquals(0.0, failureRate)
                }
            }
        }
        assertIs<CircuitBreakerEvent.RecordedSuccess>(events[eventIndex]).apply {
            assertEquals(0.5, failureRate)
        }
        val transitionToOpen = events[++eventIndex]
        assertIs<CircuitBreakerEvent.StateTransition>(transitionToOpen).apply {
            assertSame(CircuitBreakerState.Closed, fromState)
            assertIs<CircuitBreakerState.Open>(toState)
            assertFalse(manual)
        }

        // when: a call to the remote service is performed
        // then: the call should not be permitted
        assertFailsWith<CallNotPermittedException> {
            circuitBreaker.executeOperation {
                remoteService.suspendSupplier()
            }
        }
        // and: the event should be recorded
        assertIs<CircuitBreakerEvent.CallNotPermitted>(events[++eventIndex])

        // when: the delay duration has been exceeded
        delayWithRealTime(initialDelay)

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState())

        // and: the events should be recorded correctly
        val transitionToHalfOpen = events[++eventIndex]
        assertIs<CircuitBreakerEvent.StateTransition>(transitionToHalfOpen).apply {
            assertIs<CircuitBreakerState.Open>(fromState)
            assertIs<CircuitBreakerState.HalfOpen>(toState)
            assertFalse(manual)
        }

        // when: the remote service is called enough times to make up the permitted number of
        //  calls in the HalfOpen state
        repeat(config.permittedNumberOfCallsInHalfOpenState) {
            if (it == 0) {
                registerSuccess()
                assertIs<CircuitBreakerEvent.RecordedSuccess>(events[++eventIndex]).apply {
                    assertEquals(0.4, failureRate)
                }
            } else {
                registerFailure()
                assertIs<CircuitBreakerEvent.RecordedFailure>(events[++eventIndex]).apply {
                    assertEquals(0.5, failureRate)
                }
            }
        }

        // then: the circuit breaker should be in the Open state because the failure rate exceeds the threshold
        val linearDelayInSecondAttempt = initialDelay * 2
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState()).apply {
            // and: the values in this state should be correct
            assertEquals(linearDelayInSecondAttempt, delayDuration)
        }
        // and: the events should be recorded correctly
        val transitionToOpenSecond = events[++eventIndex]
        assertIs<CircuitBreakerEvent.StateTransition>(transitionToOpenSecond).apply {
            assertIs<CircuitBreakerState.HalfOpen>(fromState)
            assertIs<CircuitBreakerState.Open>(toState)
            assertFalse(manual)
        }

        // when: a call to the remote service is performed
        // then: the call should not be permitted
        assertFailsWith<CallNotPermittedException> {
            circuitBreaker.executeOperation {
                remoteService.suspendSupplier()
            }
        }
        // and: the event should be recorded
        assertIs<CircuitBreakerEvent.CallNotPermitted>(events[++eventIndex])

        // when: the delay duration has been exceeded
        delayWithRealTime(linearDelayInSecondAttempt)

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState())

        // and: the events should be recorded correctly
        val transitionToHalfOpenSecond = events[++eventIndex]
        assertIs<CircuitBreakerEvent.StateTransition>(transitionToHalfOpenSecond).apply {
            assertIs<CircuitBreakerState.Open>(fromState)
            assertIs<CircuitBreakerState.HalfOpen>(toState)
            assertFalse(manual)
        }

        // when: the remote service is called enough times to make up the permitted number of
        //  calls in the HalfOpen state
        repeat(config.permittedNumberOfCallsInHalfOpenState) {
            registerSuccess()
            assertIs<CircuitBreakerEvent.RecordedSuccess>(events[++eventIndex]).apply {
                val failurerate = if (it == 0) 0.5 else 0.4
                assertEquals(failurerate, failureRate)
            }
        }

        // then: the circuit breaker should be in the Closed state because the failure rate is below the threshold
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())
        // and: the events should be recorded correctly after some time has passed
        delayWithRealTime(10.milliseconds)
        val transitionToClosed = events[++eventIndex]
        assertIs<CircuitBreakerEvent.StateTransition>(transitionToClosed).apply {
            assertIs<CircuitBreakerState.HalfOpen>(fromState)
            assertIs<CircuitBreakerState.Closed>(toState)
            assertFalse(manual)
        }
    }

    @Test
    fun manualTransitions() = runTest {
        // given: a circuit breaker instance
        val circuitBreaker = CircuitBreaker()

        // and: events to record the state transitions
        val stateTransitionEvents = mutableListOf<CircuitBreakerEvent>()
        circuitBreaker.onStateTransition {
            stateTransitionEvents.add(it)
        }
        var index = 0
        delayWithRealTime() // wait for listeners to be registered using real time

        // when: the circuit breaker is created
        // then: the circuit breaker should be in the Closed state
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // when: the circuit breaker is manually transitioned to the Open state
        circuitBreaker.transitionToOpenState()

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())
        // and: an event should be recorded after some time has passed
        delayWithRealTime(10.milliseconds)
        assertIs<CircuitBreakerEvent.StateTransition>(stateTransitionEvents[index++]).apply {
            assertSame(CircuitBreakerState.Closed, fromState)
            assertIs<CircuitBreakerState.Open>(toState)
            assertTrue(manual)
        }

        // when: the circuit breaker is manually transitioned to the HalfOpen state
        circuitBreaker.transitionToHalfOpenState()

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState())
        // and: an event should be recorded after some time has passed
        delayWithRealTime(10.milliseconds)
        assertIs<CircuitBreakerEvent.StateTransition>(stateTransitionEvents[index++]).apply {
            assertIs<CircuitBreakerState.Open>(fromState)
            assertIs<CircuitBreakerState.HalfOpen>(toState)
            assertTrue(manual)
        }

        // when: the circuit breaker is manually transitioned to the Closed state
        circuitBreaker.transitionToClosedState()

        // then: the circuit breaker should be in the Closed state
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())
        // and: an event should be recorded
        assertIs<CircuitBreakerEvent.StateTransition>(stateTransitionEvents[index++]).apply {
            assertIs<CircuitBreakerState.HalfOpen>(fromState)
            assertSame(CircuitBreakerState.Closed, toState)
            assertTrue(manual)
        }

        // when: the circuit breaker is manually transitioned to the same Closed state
        circuitBreaker.transitionToClosedState()

        // then: the circuit breaker should be in the Closed state
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())
        // and: no event should be recorded

        // when: the circuit breaker is manually transitioned to the HalfOpen state
        circuitBreaker.transitionToHalfOpenState()

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState())
        // and: an event should be recorded
        assertIs<CircuitBreakerEvent.StateTransition>(stateTransitionEvents[index++]).apply {
            assertSame(CircuitBreakerState.Closed, fromState)
            assertIs<CircuitBreakerState.HalfOpen>(toState)
            assertTrue(manual)
        }

        // when: the circuit breaker is manually transitioned to the same HalfOpen state
        circuitBreaker.transitionToHalfOpenState()

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState())

        // when: the circuit breaker is manually transitioned to the Open state
        circuitBreaker.transitionToOpenState()

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())
        // and: an event should be recorded
        assertIs<CircuitBreakerEvent.StateTransition>(stateTransitionEvents[index++]).apply {
            assertIs<CircuitBreakerState.HalfOpen>(fromState)
            assertIs<CircuitBreakerState.Open>(toState)
            assertTrue(manual)
        }

        // when: the circuit breaker is manually transitioned to the same Open state
        circuitBreaker.transitionToOpenState()

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())

        // when: the circuit breaker is manually transitioned to the Closed state
        circuitBreaker.transitionToClosedState()

        // then: the circuit breaker should be in the Closed state
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())
        // and: an event should be recorded
        @Suppress("UNUSED_CHANGED_VALUE")
        assertIs<CircuitBreakerEvent.StateTransition>(stateTransitionEvents[index++]).apply {
            assertIs<CircuitBreakerState.Open>(fromState)
            assertSame(CircuitBreakerState.Closed, toState)
            assertTrue(manual)
        }
    }

    @Test
    fun subsequentManualTransitionsToOpenStateIncreaseCycleCount() = runTest {
        // given: a circuit breaker instance
        val circuitBreaker = CircuitBreaker()

        // and: events to record the state transitions
        val stateTransitionEvents = mutableListOf<CircuitBreakerEvent>()
        circuitBreaker.onStateTransition {
            stateTransitionEvents.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        // when: the circuit breaker is manually transitioned to the Open state
        circuitBreaker.transitionToOpenState()

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState()).apply {
            // and: the cycle count should be 1
            assertEquals(1, nrOfTransitionsToOpenStateInACycle)
        }

        // when: the circuit breaker is manually transitioned to the Open state again
        circuitBreaker.transitionToOpenState()

        // then: the circuit breaker should remain in the Open state without altering the cycle count
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState()).apply {
            assertEquals(1, nrOfTransitionsToOpenStateInACycle)
        }

        // when: the circuit breaker is manually transitioned to the HalfOpen state
        circuitBreaker.transitionToHalfOpenState()

        // then: the circuit breaker should be in the HalfOpen state
        assertIs<CircuitBreakerState.HalfOpen>(circuitBreaker.currentState()).apply {
            // and: this state should store the last cycle count
            assertEquals(1, nrOfTransitionsToOpenStateInACycle)
        }

        // when: the circuit breaker is manually transitioned to the Open state
        circuitBreaker.transitionToOpenState()

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState()).apply {
            // and: the cycle count should be 2
            assertEquals(2, nrOfTransitionsToOpenStateInACycle)
        }

        // when: the circuit breaker is manually transitioned to the Closed state
        circuitBreaker.transitionToClosedState()

        // then: the circuit breaker should be in the Closed state
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // when: the circuit breaker is manually transitioned to the Open state
        circuitBreaker.transitionToOpenState()

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState()).apply {
            // and: the cycle count should be 1, because a new cycle (closed -> open) started
            assertEquals(1, nrOfTransitionsToOpenStateInACycle)
        }

    }

    @Test
    fun aResetShouldClearTheSlidingWindow() = runTest {
        // given: a circuit breaker configuration
        val windowSize = 10
        val initialDelay = 3.seconds
        val config = circuitBreakerConfig {
            failureRateThreshold = 0.5
            slidingWindow(
                size = windowSize,
                minimumThroughput = windowSize
            )
            maxWaitDurationInHalfOpenState = ZERO
            linearDelayInOpenState(initialDelay = initialDelay, multiplier = 1.0)
            permittedNumberOfCallsInHalfOpenState = 1
            recordExceptionPredicate { it is WebServiceException }
        }

        // and: a circuit breaker instance
        val circuitBreaker = CircuitBreaker(config)
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: a remote service that always throws an exception
        val notAFailure = NetworkException("Thanks Vodafone!")
        val failure = WebServiceException("BAM!")
        val exceptionsToThrow = List(windowSize * 2) { index ->
            if (index % 2 == 0) failure
            else notAFailure // note: that only half of the calls are considered failures
        }
        coEvery { remoteService.suspendSupplier() }
            .throwsMany(*exceptionsToThrow.toTypedArray())

        // and: events to record the state transitions
        val resetEvents = mutableListOf<CircuitBreakerEvent>()
        val stateTransitionEvents = mutableListOf<CircuitBreakerEvent>()
        circuitBreaker.onStateTransition {
            stateTransitionEvents.add(it)
        }
        circuitBreaker.onReset {
            resetEvents.add(it)
        }
        delayWithRealTime() // wait for listeners to be registered using real time

        // and: functions to register a success or a failure
        suspend fun registerSuccess() {
            assertFailsWith<NetworkException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        suspend fun registerFailure() {
            assertFailsWith<WebServiceException> {
                circuitBreaker.executeOperation {
                    remoteService.suspendSupplier()
                }
            }
        }

        // when: the remote service is called multiple times until the failure rate can be calculated
        repeat(windowSize) { index ->
            if (index % 2 == 0) registerFailure()
            else registerSuccess()
        }

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())

        // when: the circuit breaker is reset
        circuitBreaker.reset()

        // then: the circuit breaker should be in the Closed state
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())

        // and: the remote service needs to be called again multiple times until the failure rate can be calculated
        repeat(windowSize) { index ->
            if (index % 2 == 0) registerFailure()
            else registerSuccess()
        }

        // then: the circuit breaker should be in the Open state
        assertIs<CircuitBreakerState.Open>(circuitBreaker.currentState())

        // and: the relevant events should be recorded correctly
        assertEquals(3, stateTransitionEvents.size)
        assertIs<CircuitBreakerEvent.StateTransition>(stateTransitionEvents[0]).apply {
            assertSame(CircuitBreakerState.Closed, fromState)
            assertIs<CircuitBreakerState.Open>(toState)
            assertFalse(manual)
        }
        assertIs<CircuitBreakerEvent.StateTransition>(stateTransitionEvents[1]).apply {
            assertIs<CircuitBreakerState.Open>(fromState)
            assertSame(CircuitBreakerState.Closed, toState)
            assertTrue(manual)
        }
        assertIs<CircuitBreakerEvent.StateTransition>(stateTransitionEvents[2]).apply {
            assertSame(CircuitBreakerState.Closed, fromState)
            assertIs<CircuitBreakerState.Open>(toState)
            assertFalse(manual)
        }
        assertEquals(1, resetEvents.size)
    }

}
