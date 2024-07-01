package kresil.circuitbreaker.state.reducer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.event.CircuitBreakerEvent
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
import kresil.core.reducer.Reducer
import kresil.core.slidingwindow.FailureRateSlidingWindow
import kresil.core.timemark.getCurrentTimeMark
import kresil.core.timemark.hasExceededDuration
import kotlin.time.Duration

/**
 * A thread-safe state machine that acts as a reducer for a [CircuitBreaker].
 * Using the [dispatch] method, events can be dispatched to the state machine to trigger state transitions.
 * The current state can be consulted using the [currentState] method.
 * @param slidingWindow the sliding window used to record the result (success or failure)
 * of operations and calculate the failure rate.
 * @param config the configuration of the circuit breaker.
 * @param events the shared flow to emit circuit breaker events to.
 */
internal class CircuitBreakerStateReducer<T>(
    val slidingWindow: FailureRateSlidingWindow<T>,
    val config: CircuitBreakerConfig,
    val events: MutableSharedFlow<CircuitBreakerEvent>,
) : Reducer<CircuitBreakerState, CircuitBreakerReducerEvent, CircuitBreakerReducerEffect>() {

    // reminder: mutexes are not reentrant and a coroutine should not suspend while holding a lock
    //  as it does not release it while suspended
    private val lock = Mutex()
    private val isAboveThreshold: Boolean
        get() = slidingWindow.currentFailureRate() >= config.failureRateThreshold
    private val isToWaitIndefinitelyInHalfOpenState: Boolean =
        config.maxWaitDurationInHalfOpenState == Duration.ZERO

    // internal state
    private var _state: CircuitBreakerState = Closed
    // reminder: sliding window is also part of the state
    // TODO: should a reference to the sliding window be part of the state object?

    override suspend fun currentState(): CircuitBreakerState = lock.withLock { _state }

    override suspend fun dispatch(event: CircuitBreakerReducerEvent): Unit = lock.withLock {
        val observedState = _state
        val (newState, _) = reducer(observedState, event)
        _state = newState
        if (observedState != newState) {
            events.emit(CircuitBreakerEvent.StateTransition(observedState, newState, event.isTransitionEvent))
        }
    }

    override suspend fun reducer(
        state: CircuitBreakerState,
        event: CircuitBreakerReducerEvent,
    ): Pair<CircuitBreakerState, List<CircuitBreakerReducerEffect>> {
        // no effects are used in this implementation
        val effects = emptyList<CircuitBreakerReducerEffect>()
        val maintainStateWithNoEffects = state to emptyList<CircuitBreakerReducerEffect>()
        when (state) {
            Closed -> {
                when (event) {
                    FORCE_STATE_UPDATE, TRANSITION_TO_CLOSED_STATE -> {
                        return maintainStateWithNoEffects
                    }
                    TRANSITION_TO_OPEN_STATE -> return createOpenState(state) to effects
                    TRANSITION_TO_HALF_OPEN_STATE -> return createHalfOpenState(state) to effects
                    RESET -> return reset() to effects
                    OPERATION_SUCCESS -> recordSuccess()
                    OPERATION_FAILURE -> recordFailure()
                }
                return if (isAboveThreshold) {
                    createOpenState(state) to effects
                } else {
                    maintainStateWithNoEffects
                }
            }

            is Open -> {
                return when (event) {
                    FORCE_STATE_UPDATE -> if (hasExceededDurationInOpenState(state)) {
                        createHalfOpenState(state) to effects
                    } else {
                        maintainStateWithNoEffects
                    }

                    TRANSITION_TO_CLOSED_STATE -> Closed to effects
                    TRANSITION_TO_HALF_OPEN_STATE -> createHalfOpenState(state) to effects
                    RESET -> reset() to effects
                    TRANSITION_TO_OPEN_STATE, OPERATION_SUCCESS, OPERATION_FAILURE -> {
                        maintainStateWithNoEffects
                    }
                }
            }

            is HalfOpen -> {
                when (event) {
                    FORCE_STATE_UPDATE -> return if (hasExceededDurationInHalfOpenState(state)) {
                        createOpenState(state) to effects
                    } else {
                        maintainStateWithNoEffects
                    }
                    TRANSITION_TO_CLOSED_STATE -> return Closed to effects
                    TRANSITION_TO_OPEN_STATE -> return createOpenState(state) to effects
                    TRANSITION_TO_HALF_OPEN_STATE -> return maintainStateWithNoEffects
                    RESET -> return reset() to effects
                    OPERATION_SUCCESS -> recordSuccess()
                    OPERATION_FAILURE -> recordFailure()
                }
                val nrOfCallsAttempted = state.nrOfCallsAttempted + 1
                return if (nrOfCallsAttempted >= config.permittedNumberOfCallsInHalfOpenState) {
                    if (isAboveThreshold) {
                        createOpenState(state) to effects
                    } else {
                        Closed to effects
                    }
                } else {
                    state.copy(nrOfCallsAttempted = nrOfCallsAttempted) to effects
                }
            }
        }
    }

    // helper functions:
    private suspend fun reset(): CircuitBreakerState {
        slidingWindow.clear()
        events.emit(CircuitBreakerEvent.Reset) // TODO: again.. emitting inside the reducer is a side effect
        return Closed
    }

    private suspend fun createOpenState(state: CircuitBreakerState): CircuitBreakerState =
        when (state) {
            Closed -> 1
            is HalfOpen -> state.nrOfTransitionsToOpenStateInACycle + 1
            else -> error("Trying to create an ${Open::class.simpleName} state from an invalid state: $state")
        }.let {
            Open(
                delayDuration = config.delayStrategyInOpenState(it, Unit),
                startTimerMark = getCurrentTimeMark(),
                nrOfTransitionsToOpenStateInACycle = it
            )
        }

    private fun createHalfOpenState(state: CircuitBreakerState): CircuitBreakerState {
        val delayDurationInHalfOpenState = if (isToWaitIndefinitelyInHalfOpenState) {
            null
        } else {
            getCurrentTimeMark()
        }
        val nrOfTransitionsToOpenStateInACycle = when (state) {
            is Open -> state.nrOfTransitionsToOpenStateInACycle
            is Closed -> 1
            else -> error("Trying to create a ${HalfOpen::class.simpleName} state from an invalid state: $state")
        }
        return HalfOpen(
            nrOfCallsAttempted = 0,
            startTimerMark = delayDurationInHalfOpenState,
            nrOfTransitionsToOpenStateInACycle = nrOfTransitionsToOpenStateInACycle
        )
    }

    private suspend fun recordSuccess() {
        slidingWindow.recordSuccess()
        val currentFailureRate = slidingWindow.currentFailureRate()
        events.emit(CircuitBreakerEvent.RecordedSuccess(currentFailureRate))
    }

    private suspend fun recordFailure() {
        slidingWindow.recordFailure()
        val currentFailureRate = slidingWindow.currentFailureRate()
        events.emit(CircuitBreakerEvent.RecordedFailure(currentFailureRate))
    }

    private fun hasExceededDurationInHalfOpenState(state: CircuitBreakerState): Boolean =
        when (state) {
            is HalfOpen -> state.startTimerMark != null &&
                    hasExceededDuration(state.startTimerMark, config.maxWaitDurationInHalfOpenState)

            else -> error("Trying to check if the duration has exceeded in a state that is not ${HalfOpen::class.simpleName}: $state")
        }

    private fun hasExceededDurationInOpenState(state: CircuitBreakerState): Boolean =
        when (state) {
            is Open -> hasExceededDuration(state.startTimerMark, state.delayDuration)
            else -> error("Trying to check if the duration has exceeded in a state that is not ${Open::class.simpleName}: $state")
        }

}
