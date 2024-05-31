package circuitbreaker

import exceptions.NetworkException
import exceptions.WebServiceException
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.config.circuitBreakerConfig
import kresil.circuitbreaker.exceptions.CallNotPermittedException
import kresil.circuitbreaker.state.CircuitBreakerState
import service.RemoteService
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class CircuitBreakerTests {

    @Mock
    val remoteService = mock(classOf<RemoteService>())

    @Test
    fun circuitBreakerShouldOpenWhenFailureRateExceedsThreshold() =
        circuitBreakerShouldOpenWhenFailureRateExceedsThresholdAndMinimumThroughputIsMet(0.9)

    @Test
    fun circuitBreakerShouldOpenWhenFailureRateEqualsTheMaximumThreshold() =
        circuitBreakerShouldOpenWhenFailureRateExceedsThresholdAndMinimumThroughputIsMet(1.0)

    private fun circuitBreakerShouldOpenWhenFailureRateExceedsThresholdAndMinimumThroughputIsMet(
        failureRateThreshold: Double,
    ) = runTest {
        // given: a circuit breaker configuration
        val nrOfCallsToCalculateFailureRate = 1000
        val config = circuitBreakerConfig {
            this.failureRateThreshold = failureRateThreshold
            slidingWindow(
                size = nrOfCallsToCalculateFailureRate * 2,
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
        val nrOfCallsToCalculateFailureRate = 1000
        val config = circuitBreakerConfig {
            failureRateThreshold = 0.51 // 0.5 would trigger the Open state
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
            else WebServiceException("BAM!") // only half of the calls are considered failures
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

        // and: the circuit breaker should be in the Closed state
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
            circuitBreaker.executeOperation<String?> { // TODO: why does this needs to be parameterized?
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
            failureRateThreshold = 0.00001 // low threshold to trigger the Open state quickly
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
        //  because the recorded exceptions in the sliding window are now considered successes
        assertSame(CircuitBreakerState.Closed, circuitBreaker.currentState())
    }

    // TODO half Open state tests (back and forth between Open and Closed)

}
