package kresil.circuitbreaker.state.reducer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.state.CircuitBreakerState
import kresil.circuitbreaker.state.CircuitBreakerState.Closed
import kresil.circuitbreaker.state.CircuitBreakerState.HalfOpen
import kresil.circuitbreaker.state.CircuitBreakerState.Open
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.OPERATION_FAILURE
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.OPERATION_SUCCESS
import kresil.core.reducer.Reducer
import kresil.core.slidingwindow.FailureRateSlidingWindow
import kotlin.time.ComparableTimeMark
import kotlin.time.TestTimeSource

/**
 * A thread-safe state machine that acts as a reducer for a [CircuitBreaker].
 * Using the [dispatch] method, events can be dispatched to the state machine to trigger state transitions.
 * The current state can be consulted using the [currentState] method.
 * @param slidingWindow the sliding window used to record the success or failure of calls and calculate the failure rate.
 * @param config the configuration of the circuit breaker.
 */
class CircuitBreakerStateReducer<T>(
    private val slidingWindow: FailureRateSlidingWindow<T>,
    private val config: CircuitBreakerConfig,
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
    private var openStateStartTimeMark: ComparableTimeMark? = null
    private var halfStateStartTimeMark: ComparableTimeMark? = null

    // internal events (that trigger state transitions)
    private val hasExceededDurationInOpenState: Boolean
        get() = openStateStartTimeMark?.let {
            it.elapsedNow() >= config.waitDurationInOpenState
        } ?: false

    private val hasExceededDurationInHalfOpenState: Boolean
        get() = halfStateStartTimeMark?.let {
            it.elapsedNow() >= config.maxWaitDurationInHalfOpenState
        } ?: false

    override suspend fun currentState(): CircuitBreakerState = lock.withLock {
        // TODO: if reducers should be pure functions, where can an internal event be dispatched?
        checkForInternalEvents()
        _state
    }

    override suspend fun dispatch(event: CircuitBreakerReducerEvent): Unit = lock.withLock {
        when (val state = _state) {
            Closed -> when (event) {
                OPERATION_SUCCESS -> slidingWindow.recordSuccess()
                OPERATION_FAILURE -> {
                    slidingWindow.recordFailure()
                    if (slidingWindow.currentFailureRate() >= config.failureRateThreshold) {
                        transitionToOpenState()
                    }
                }
            }

            Open -> noOperation

            is HalfOpen -> {
                when (event) {
                    OPERATION_SUCCESS -> noOperation
                    // if one of the calls made in the HalfOpen state fails,
                    // transition back to the Open state
                    OPERATION_FAILURE -> {
                        transitionToOpenState()
                        return@withLock
                    }
                }
                val nrOfCallsAttempted = state.nrOfCallsAttempted + 1
                if (nrOfCallsAttempted >= config.permittedNumberOfCallsInHalfOpenState) {
                    transitionToClosedState()
                } else {
                    transitionToHalfOpenState(nrOfCallsAttempted)
                }
            }
        }
    }

    private fun checkForInternalEvents() {
        when (_state) {
            Closed -> noOperation
            Open -> if (hasExceededDurationInOpenState) transitionToHalfOpenState(0)
            is HalfOpen -> if (hasExceededDurationInHalfOpenState) transitionToOpenState()
        }
    }

    private fun transitionToOpenState() {
        _state = Open
        openStateStartTimeMark = TestTimeSource().markNow()
    }

    private fun transitionToClosedState() {
        slidingWindow.clear()
        _state = Closed
    }

    private fun transitionToHalfOpenState(nrOfCallsAttempted: Int) {
        _state = HalfOpen(nrOfCallsAttempted)
        halfStateStartTimeMark = TestTimeSource().markNow()
    }
}
