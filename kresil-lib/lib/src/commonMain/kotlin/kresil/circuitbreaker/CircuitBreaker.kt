package kresil.circuitbreaker

import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.config.defaultCircuitBreakerConfig
import kresil.circuitbreaker.exceptions.CircuitBreakerOpenException
import kresil.circuitbreaker.slidingwindow.CountBasedSlidingWindow
import kresil.circuitbreaker.state.CircuitBreakerReducerEvent.OPERATION_FAILURE
import kresil.circuitbreaker.state.CircuitBreakerReducerEvent.OPERATION_SUCCESS
import kresil.circuitbreaker.state.CircuitBreakerState.CLOSED
import kresil.circuitbreaker.state.CircuitBreakerState.HALF_OPEN
import kresil.circuitbreaker.state.CircuitBreakerState.OPEN
import kresil.circuitbreaker.state.CircuitBreakerStateReducer

/**
 * A [Circuit Breaker](https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker)
 * resilience mechanism implementation, that can be used to protect a system component from overloading or failing.
 * State management is done via a finite state machine implemented using the reducer pattern,
 * where events are dispatched to the reducer to change the state of the circuit breaker based
 * on the current state and the event.
 * This way, two or more circuit breakers can use the same reducer to manage their state
 * (e.g. when one of two or more related components fails, the others are likely to fail too).
 * A circuit breaker is initialized with a [CircuitBreakerConfig] that,
 * through pre-configured policies, define its behaviour.
 * The circuit breaker implements the following state machine:
 * ```
 *               failure rate
 * +--------+   exceeds threshold   +------+
 * | Closed | --------------------> | Open |
 * +--------+                       +------+
 *     ^                               |  ^
 *     |                         after |  |  failure rate
 *     |                       timeout |  |  exceeds threshold
 *     |                               v  |
 *     |         after timeout      +-----------+
 *     |--------------------------- | Half-Open |
 *                                  +-----------+
 * ```
 * - In the [CLOSED] state, the circuit breaker allows calls to execute the underlying operation, while
 * recording the success or failure of these calls. When the failure rate exceeds the threshold, the
 * circuit breaker transitions to the [OPEN] state.
 * - In the [OPEN] state,
 * the circuit breaker rejects all received calls for a (configurable) amount of time and then transitions
 * to the [HALF_OPEN] state.
 * - In the [HALF_OPEN] state,
 * the circuit breaker allows a (configurable) number of calls to test if the underlying operation is still failing.
 * If the failure rate exceeds the threshold, the circuit breaker transitions back to the [OPEN] state.
 * In a (configurable) amount of time, the circuit breaker transitions back to the [CLOSED] state if the failure
 * rate is below the threshold.
 *
 * Examples of usage:
 * ```
 * // use predefined policies
 * val circuitBreaker = CircuitBreaker()
 *
 * // use custom policies
 * val circuitBreaker = CircuitBreaker(
 *   circuitBreakerConfig {
 *     slidingWindowSize = 10
 *     minimumThroughput = 5
 *     failureRateThreshold = 0.5
 *     waitDurationInOpenState = 10.seconds
 *     recordSuccessAsFailurePredicate { it is "success" }
 *     recordExceptionPredicate { it is NetworkError }
 *   }
 * )
 *
 * // execute an operation
 * val result = circuitBreaker.executeOperation {
 *  // operation
 * }
 *
 */
class CircuitBreaker(
    val config: CircuitBreakerConfig = defaultCircuitBreakerConfig(),
) { // TODO: needs to implement flow event listener

    private val slidingWindow = CountBasedSlidingWindow(
        capacity = config.slidingWindowSize,
        minimumThroughput = config.minimumThroughput,
    )
    private val stateReducer = CircuitBreakerStateReducer(slidingWindow, config)

    suspend fun currentState() = stateReducer.currentState()

    /**
     * Executes the given operation decorated by this circuit breaker and returns its result,
     * while handling any possible failure and emitting the necessary events.
     */
    // TODO: introduce all operation types here later (Supplier, Function, BiFunction)
    suspend fun <R> executeOperation(block: suspend () -> R): R =
        when (stateReducer.currentState()) {
            OPEN -> {
                throw CircuitBreakerOpenException()
            }

            CLOSED, HALF_OPEN -> {
                val result = safeExecute(block)
                if (config.recordSuccessAsFailurePredicate(result)) {
                    stateReducer.dispatch(OPERATION_FAILURE)
                } else {
                    stateReducer.dispatch(OPERATION_SUCCESS)
                }
                result
            }
        }

    /**
     * Executes the given operation and returns its result, while handling any possible failure.
     */
    private suspend fun <R> safeExecute(block: suspend () -> R): R =
        try {
            block()
        } catch (e: Throwable) {
            handleFailure(e)
        }

    /**
     * Handles the possible failure of an operation decorated by the circuit breaker, by emitting the necessary
     * events and rethrowing the exception.
     */
    private suspend fun <R> handleFailure(throwable: Throwable): R =
        when (stateReducer.currentState()) {
            OPEN -> {
                throw CircuitBreakerOpenException()
            }

            CLOSED, HALF_OPEN -> {
                if (config.recordExceptionPredicate(throwable)) {
                    stateReducer.dispatch(OPERATION_FAILURE)
                } else {
                    stateReducer.dispatch(OPERATION_SUCCESS)
                }
                throw throwable
            }
        }

}
