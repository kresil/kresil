package kresil.circuitbreaker

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.config.defaultCircuitBreakerConfig
import kresil.circuitbreaker.event.CircuitBreakerEvent
import kresil.circuitbreaker.exceptions.CallNotPermittedException
import kresil.circuitbreaker.slidingwindow.CountBasedSlidingWindow
import kresil.circuitbreaker.slidingwindow.SlidingWindowType
import kresil.circuitbreaker.state.CircuitBreakerState
import kresil.circuitbreaker.state.CircuitBreakerState.Closed
import kresil.circuitbreaker.state.CircuitBreakerState.HalfOpen
import kresil.circuitbreaker.state.CircuitBreakerState.Open
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.OPERATION_FAILURE
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.OPERATION_SUCCESS
import kresil.circuitbreaker.state.reducer.CircuitBreakerStateReducer
import kresil.core.events.FlowEventListenerImpl

/**
 * A [Circuit Breaker](https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker)
 * resilience mechanism implementation that can be used to protect a system component from overloading or failing.
 * State management is done via a finite state machine implemented using the reducer pattern,
 * where events are dispatched to the reducer to change the state of the circuit breaker based
 * on the current state and the event.
 * This way, two or more circuit breakers can use the same reducer to manage their state
 * (i.e., when one of two or more related components fail, the others are likely to fail too).
 * A circuit breaker is initialized with a [CircuitBreakerConfig] that,
 * through pre-configured policies, define its behaviour.
 * The circuit breaker implements the following state machine:
 * ```
 *             failure rate exceeds
 * +--------+  or equals threshold   +------+
 * | Closed | ---------------------> | Open |
 * +--------+                        +------+
 *     ^                               |  ^
 *     |                         after |  |  failure rate
 *     |                       timeout |  |  exceeds or
 *     |                               |  |  equals threshold
 *     |       failure rate            v  |
 *     |       below threshold     +-----------+
 *     |-------------------------- | Half-Open |
 *                                 +-----------+
 * ```
 * - In the [Closed] state, the circuit breaker allows calls to execute the underlying operation, while
 * recording the success or failure of these calls.
 * When the failure rate exceeds a (configurable) threshold, the
 * circuit breaker transitions to the [Open] state.
 * - In the [Open] state,
 * the circuit breaker rejects all received calls for a (configurable) amount of time and then transitions
 * to the [HalfOpen] state.
 * - In the [HalfOpen] state,
 * the circuit breaker allows a (configurable) number of calls to test if the underlying operation is still failing.
 * After all calls have been attempted, the circuit breaker transitions back to the [Open] state if newly calculated
 * failure rate exceeds or equals the threshold; otherwise, it transitions to the [Closed] state.
 *
 * Examples of usage:
 * ```
 * // use predefined policies
 * val circuitBreaker = CircuitBreaker()
 *
 * // use custom policies
 * val circuitBreaker = CircuitBreaker(
 *   circuitBreakerConfig {
 *     failureRateThreshold = 0.5
 *     slidingWindow(size = 10, minimumThroughput = 10)
 *     waitDurationInOpenState = 10.seconds
 *     recordResultPredicate { it is "success" }
 *     recordExceptionPredicate { it is NetworkError }
 *   }
 * )
 *
 * // get the current state of the circuit breaker
 * val observedState = circuitBreaker.currentState()
 *
 * // execute an operation under the circuit breaker
 * val result = circuitBreaker.executeOperation {
 *   // operation
 * }
 *
 * // listen to specific events
 * circuitBreaker.onCallNotPermitted {
 *   // action
 * }
 *
 * // listen to all events
 * circuitBreaker.onEvent {
 *   // action
 * }
 *
 * // cancel all registered listeners
 * circuitBreaker.cancelListeners()
 */
class CircuitBreaker(
    val config: CircuitBreakerConfig = defaultCircuitBreakerConfig(),
) : FlowEventListenerImpl<CircuitBreakerEvent>() {

    private val slidingWindow = when (config.slidingWindow.type) {
        SlidingWindowType.COUNT_BASED -> CountBasedSlidingWindow(
            config.slidingWindow.size,
            config.slidingWindow.minimumThroughput
        )

        SlidingWindowType.TIME_BASED -> TODO()
    }
    val stateReducer = CircuitBreakerStateReducer(slidingWindow, config, events)

    suspend fun currentState() = stateReducer.currentState()

    /**
     * Wires the circuit breaker to the operation to be protected.
     * If the circuit breaker is in the [Open] state or in the [HalfOpen] state and the number of calls attempted
     * does exceed the permitted number of calls in the half-open state, a [CallNotPermittedException] is thrown;
     * otherwise, the operation is allowed to proceed.
     */
    suspend fun wire() = when (val observedState = currentState()) {
        Closed -> Unit
        is Open -> throwCallIsNotPermitted()
        is HalfOpen -> {
            if (observedState.nrOfCallsAttempted >= config.permittedNumberOfCallsInHalfOpenState) {
                throwCallIsNotPermitted()
            }
            Unit
        }
    }

    /**
     * Executes the given operation decorated by this circuit breaker and returns its result,
     * while handling any possible failure and emitting the necessary events.
     */
    // TODO: add resultMapper to map the result of the operation
    suspend fun <R> executeOperation(block: suspend () -> R): R {
        wire()
        return executeAndDispatch(block)
    }

    /**
     * Executes the given operation and dispatches the necessary events based on its
     * result and the configuration.
     */
    private suspend fun <R> executeAndDispatch(block: suspend () -> R): R {
        val result = safeExecute(block)
        if (config.recordResultPredicate(result)) {
            stateReducer.dispatch(OPERATION_FAILURE)
        } else {
            stateReducer.dispatch(OPERATION_SUCCESS)
        }
        return result
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
        when (currentState()) {
            is Open -> throwCallIsNotPermitted()
            Closed, is HalfOpen -> {
                if (config.recordExceptionPredicate(throwable)) {
                    stateReducer.dispatch(OPERATION_FAILURE)
                } else {
                    stateReducer.dispatch(OPERATION_SUCCESS)
                }
                throw throwable
            }
        }

    private suspend fun throwCallIsNotPermitted(): Nothing {
        events.emit(CircuitBreakerEvent.CallNotPermitted)
        throw CallNotPermittedException()
    }

    /**
     * Executes the given [action] when a request is not permitted by the circuit breaker, either
     * because it is in the [Open] state or the permitted number of calls in the [HalfOpen] state has been exceeded and
     * the circuit breaker is still in the [HalfOpen] state.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onCallNotPermitted(action: suspend () -> Unit) =
        scope.launch {
            events
                .filterIsInstance<CircuitBreakerEvent.CallNotPermitted>()
                .collect { action() }
        }

    /**
     * Executes the given [action] when the circuit breaker resets.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onReset(action: suspend () -> Unit) =
        scope.launch {
            events
                .filterIsInstance<CircuitBreakerEvent.Reset>()
                .collect { action() }
        }

    /**
     * Executes the given [action] when the circuit breaker transitions to a new state.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onStateTransition(
        action: suspend (
            fromState: CircuitBreakerState,
            toState: CircuitBreakerState,
            manual: Boolean,
        ) -> Unit,
    ) = scope.launch {
            events
                .filterIsInstance<CircuitBreakerEvent.StateTransition>()
                .collect { action(it.fromState, it.toState, it.manual) }
        }

    /**
     * Executes the given [action] when an operation succeeds.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onOperationSuccess(action: suspend () -> Unit) =
        scope.launch {
            events
                .filterIsInstance<CircuitBreakerEvent.RecordedSuccess>()
                .collect { action() }
        }

    /**
     * Executes the given [action] when an operation fails.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onOperationFailure(action: suspend () -> Unit) =
        scope.launch {
            events
                .filterIsInstance<CircuitBreakerEvent.RecordedFailure>()
                .collect { action() }
        }

}
