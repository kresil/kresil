package kresil.circuitbreaker

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.config.defaultCircuitBreakerConfig
import kresil.circuitbreaker.event.CircuitBreakerEvent
import kresil.circuitbreaker.event.CircuitBreakerEvent.CallNotPermitted
import kresil.circuitbreaker.event.CircuitBreakerEvent.RecordedFailure
import kresil.circuitbreaker.event.CircuitBreakerEvent.RecordedSuccess
import kresil.circuitbreaker.event.CircuitBreakerEvent.Reset
import kresil.circuitbreaker.event.CircuitBreakerEvent.StateTransition
import kresil.circuitbreaker.exceptions.CallNotPermittedException
import kresil.circuitbreaker.state.slidingwindow.CountBasedSlidingWindow
import kresil.circuitbreaker.state.slidingwindow.SlidingWindowType
import kresil.circuitbreaker.state.CircuitBreakerState
import kresil.circuitbreaker.state.CircuitBreakerState.Closed
import kresil.circuitbreaker.state.CircuitBreakerState.HalfOpen
import kresil.circuitbreaker.state.CircuitBreakerState.Open
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.FORCE_STATE_UPDATE
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.OPERATION_FAILURE
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.OPERATION_SUCCESS
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.RESET
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.TRANSITION_TO_CLOSED_STATE
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.TRANSITION_TO_HALF_OPEN_STATE
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.TRANSITION_TO_OPEN_STATE
import kresil.circuitbreaker.state.reducer.CircuitBreakerStateReducer
import kresil.core.events.FlowEventListenerImpl

/**
 * The [Circuit Breaker](https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker)
 * is a **reactive** resilience mechanism
 * that can be used to protect a system component from overloading or failing.
 * By monitoring the health of the system, the circuit breaker can short-circuit
 * execution requests when it detects that the system component is not behaving as expected.
 * After a configurable timeout,
 * the circuit breaker allows a limited number of test requests to pass through to see if the system has recovered.
 * Depending on the test results, the circuit breaker can resume normal operation or continue to short-circuit requests.
 * A circuit breaker is initialized with a configuration that,
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
 * // wire the circuit breaker
 * circuitBreaker.wire()
 *
 * // execute an operation under the circuit breaker
 * val result = circuitBreaker.executeOperation {
 *     // operation
 * }
 *
 * // listen to specific events
 * circuitBreaker.onCallNotPermitted {
 *     // action
 * }
 *
 * // listen to all events
 * circuitBreaker.onEvent {
 *     // action
 * }
 *
 * // cancel all registered listeners
 * circuitBreaker.cancelListeners()
 *
 * // manually:
 * // - override the circuit breaker state
 * circuitBreaker.transitionToOpen()
 * // - reset the circuit breaker
 * circuitBreaker.reset()
 * // - record an operation success
 * circuitBreaker.recordSuccess()
 * // - record an operation failure
 * circuitBreaker.recordFailure()
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

    private val stateReducer = CircuitBreakerStateReducer(slidingWindow, config, events)

    /**
     * Records a successful operation execution.
     */
    suspend fun recordSuccess() {
        stateReducer.dispatch(OPERATION_SUCCESS)
    }

    /**
     * Records a failed operation execution.
     */
    suspend fun recordFailure() {
        stateReducer.dispatch(OPERATION_FAILURE)
    }

    /**
     * Returns the current state of the circuit breaker as a snapshot.
     */
    suspend fun currentState(): CircuitBreakerState {
        stateReducer.dispatch(FORCE_STATE_UPDATE)
        return stateReducer.currentState()
    }

    /**
     * Checks the current state of the circuit breaker and decides whether the operation is allowed to proceed.
     * If the circuit breaker is in the [Open] state or in the [HalfOpen] state and the number of calls attempted
     * does exceed the permitted number of calls in the half-open state, a [CallNotPermittedException] is thrown;
     * otherwise, the operation is allowed to proceed.
     * It also updates the state before throwing the exception.
     */
    suspend fun wire(): Unit = when (val observedState = currentState()) {
        Closed -> Unit
        is Open -> throwCallIsNotPermitted()
        is HalfOpen -> if (observedState.nrOfCallsAttempted >= config.permittedNumberOfCallsInHalfOpenState) {
            throwCallIsNotPermitted()
        } else {
            Unit
        }
    }

    /**
     * Executes the given operation decorated by this circuit breaker and returns its result,
     * while handling any possible failure and emitting the necessary events.
     */
    // TODO: add exceptionHandler to map the result of the operation
    suspend fun <R> executeOperation(block: suspend () -> R): R {
        wire()
        return executeAndDispatch(block)
    }

    /**
     * Transitions the circuit breaker to the [Open] state, maintaining the recorded results.
     */
    suspend fun transitionToOpenState() {
        stateReducer.dispatch(TRANSITION_TO_OPEN_STATE)
    }

    /**
     * Transitions the circuit breaker to the [HalfOpen] state, maintaining the recorded results.
     */
    suspend fun transitionToHalfOpenState() {
        stateReducer.dispatch(TRANSITION_TO_HALF_OPEN_STATE)
    }

    /**
     * Transitions the circuit breaker to the [Closed] state, maintaining the recorded results.
     */
    suspend fun transitionToClosedState() {
        stateReducer.dispatch(TRANSITION_TO_CLOSED_STATE)
    }

    /**
     * Resets the circuit breaker to the [Closed] state, clearing the sliding window of any recorded results.
     */
    suspend fun reset() {
        stateReducer.dispatch(RESET)
    }

    /**
     * Executes the given operation and dispatches the necessary events based on its
     * result and the configuration.
     */
    private suspend fun <R> executeAndDispatch(block: suspend () -> R): R {
        val result = safeExecute(block)
        if (config.recordResultPredicate(result)) {
            recordFailure()
        } else {
            recordSuccess()
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
                    recordFailure()
                } else {
                    recordSuccess()
                }
                throw throwable
            }
        }

    private suspend fun throwCallIsNotPermitted(): Nothing {
        events.emit(CallNotPermitted)
        throw CallNotPermittedException()
    }

    /**
     * Executes the given [action] when a request is not permitted by the circuit breaker, either
     * because it is in the [Open] state or the permitted number of calls in the [HalfOpen] state has been exceeded and
     * the circuit breaker is still in the [HalfOpen] state.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onCallNotPermitted(action: suspend (CallNotPermitted) -> Unit) =
        scope.launch {
            events
                .filterIsInstance<CallNotPermitted>()
                .collect { action(it) }
        }

    /**
     * Executes the given [action] when the circuit breaker resets.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onReset(action: suspend (Reset) -> Unit) =
        scope.launch {
            events
                .filterIsInstance<Reset>()
                .collect { action(it) }
        }

    /**
     * Executes the given [action] when the circuit breaker transitions to a new state.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onStateTransition(
        action: suspend (StateTransition) -> Unit,
    ) = scope.launch {
        events
            .filterIsInstance<StateTransition>()
            .collect { action(it) }
    }

    /**
     * Executes the given [action] when an operation succeeds.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onOperationSuccess(action: suspend (RecordedSuccess) -> Unit) =
        scope.launch {
            events
                .filterIsInstance<RecordedSuccess>()
                .collect { action(it) }
        }

    /**
     * Executes the given [action] when an operation fails.
     * @see [onEvent]
     * @see [cancelListeners]
     */
    suspend fun onOperationFailure(action: suspend (RecordedFailure) -> Unit) =
        scope.launch {
            events
                .filterIsInstance<RecordedFailure>()
                .collect { action(it) }
        }

    /**
     * Executes the given [action] when a circuit breaker event occurs.
     * This function can be used to listen to all retry events.
     * @see [onCallNotPermitted]
     * @see [onReset]
     * @see [onStateTransition]
     * @see [onOperationSuccess]
     * @see [onOperationFailure]
     * @see [cancelListeners]
     */
    override suspend fun onEvent(action: suspend (CircuitBreakerEvent) -> Unit) {
        super.onEvent(action)
    }

}
