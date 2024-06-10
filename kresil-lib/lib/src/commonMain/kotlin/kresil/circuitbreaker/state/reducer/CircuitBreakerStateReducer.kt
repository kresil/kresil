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
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.OPERATION_FAILURE
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.OPERATION_SUCCESS
import kresil.core.reducer.Reducer
import kresil.core.slidingwindow.FailureRateSlidingWindow
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A thread-safe state machine that acts as a reducer for a [CircuitBreaker].
 * Using the [dispatch] method, events can be dispatched to the state machine to trigger state transitions.
 * The current state can be consulted using the [currentState] method.
 * @param slidingWindow the sliding window used to record the result (success or failure)
 * of operations and calculate the failure rate.
 * @param config the configuration of the circuit breaker.
 * @param events the shared flow to emit circuit breaker events to.
 */
class CircuitBreakerStateReducer<T> internal constructor(
    val slidingWindow: FailureRateSlidingWindow<T>,
    val config: CircuitBreakerConfig,
    val events: MutableSharedFlow<CircuitBreakerEvent>
) : Reducer<CircuitBreakerState, CircuitBreakerReducerEvent> {

    private companion object {
        // signals that no operation should be performed
        val noOperation = {}
    }

    // reminder: mutexes are not reentrant and a coroutine should not suspend while holding a lock
    //  as it does not release it while suspended
    private val lock = Mutex()

    // internal state
    private var _state: CircuitBreakerState = Closed
    private var nrOfTransitionsToOpenState: Int = 0

    // internal events (that trigger state transitions)
    private fun hasExceededDurationInState(timeMark: ComparableTimeMark, duration: Duration): Boolean =
        timeMark.elapsedNow() >= duration

    override suspend fun currentState(): CircuitBreakerState = lock.withLock {
        // TODO: if reducers should be pure functions, where can an internal event be dispatched?
        checkForInternalEvents()
        _state
    }

    override suspend fun dispatch(event: CircuitBreakerReducerEvent): Unit = lock.withLock {
        when (val state = _state) {
            Closed -> {
                when (event) {
                    OPERATION_SUCCESS -> recordSuccess()
                    OPERATION_FAILURE -> recordFailure()
                }
                if (slidingWindow.currentFailureRate() >= config.failureRateThreshold) {
                    transitionToOpenState(false)
                }
            }

            is Open -> noOperation

            is HalfOpen -> {
                when (event) {
                    OPERATION_SUCCESS -> recordSuccess()
                    OPERATION_FAILURE -> recordFailure()
                }
                val nrOfCallsAttempted = state.nrOfCallsAttempted + 1
                if (nrOfCallsAttempted >= config.permittedNumberOfCallsInHalfOpenState) {
                    if (slidingWindow.currentFailureRate() >= config.failureRateThreshold) {
                        transitionToOpenState(true)
                    } else {
                        transitionToClosedState()
                    }
                } else {
                    transitionToHalfOpenState(nrOfCallsAttempted)
                }
            }
        }
    }

    private suspend fun checkForInternalEvents() {
        when (val state = _state) {
            Closed -> noOperation
            is Open -> if (hasExceededDurationInState(state.startTimerMark, state.delayDuration)) {
                transitionToHalfOpenState(0)
            }

            is HalfOpen -> {
                if (state.startTimerMark != null &&
                    hasExceededDurationInState(state.startTimerMark, config.maxWaitDurationInHalfOpenState)
                ) {
                    transitionToOpenState(true)
                }
            }
        }
    }

    private suspend fun transitionToOpenState(fromHalfOpenState: Boolean) {
        if (fromHalfOpenState) {
            nrOfTransitionsToOpenState++
        } else {
            nrOfTransitionsToOpenState = 1
        }
        val nextDelayDurationInOpenState = config.delayStrategyInOpenState(nrOfTransitionsToOpenState, Unit)
        val openStateStartTimeMark = TimeSource.Monotonic.markNow()
        val oldState = _state
        _state = Open(nextDelayDurationInOpenState, openStateStartTimeMark)
        events.emit(CircuitBreakerEvent.StateTransition(oldState, _state, false))
    }

    private suspend fun transitionToClosedState() {
        // TODO: should sliding window be cleared when transitioning to closed state?
        //  a test will fail if it is cleared
        val oldState = _state
        _state = Closed
        events.emit(CircuitBreakerEvent.StateTransition(oldState, _state, false))
    }

    private suspend fun transitionToHalfOpenState(nrOfCallsAttempted: Int) {
        val oldState = _state
        _state = if (config.maxWaitDurationInHalfOpenState == Duration.ZERO) {
            HalfOpen(nrOfCallsAttempted, null)
        } else {
            val halfStateStartTimeMark = TimeSource.Monotonic.markNow()
            HalfOpen(nrOfCallsAttempted, halfStateStartTimeMark)
        }
        if (oldState !is HalfOpen) {
            events.emit(CircuitBreakerEvent.StateTransition(oldState, _state, false))
        }
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

}
