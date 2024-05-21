package kresil.circuitbreaker.state

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.state.CircuitBreakerReducerEvent.OPERATION_FAILURE
import kresil.circuitbreaker.state.CircuitBreakerReducerEvent.OPERATION_SUCCESS
import kresil.circuitbreaker.state.CircuitBreakerState.Closed
import kresil.circuitbreaker.state.CircuitBreakerState.HalfOpen
import kresil.circuitbreaker.state.CircuitBreakerState.Open
import kresil.core.reducer.Reducer
import kresil.core.slidingwindow.FailureRateSlidingWindow
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TestTimeSource

/**
 * A thread-safe state machine that acts as a reducer for a [CircuitBreaker].
 * Using the [dispatch] method, events can be dispatched to the state machine to trigger state transitions.
 * The current state can be consulted using the [currentState] method.
 * @param slidingWindow the sliding window used to calculate the failure rate.
 * @param config the configuration of the circuit breaker.
 */
class CircuitBreakerStateReducer<T>(
    private val slidingWindow: FailureRateSlidingWindow<T>,
    private val config: CircuitBreakerConfig,
) : Reducer<CircuitBreakerState, CircuitBreakerReducerEvent> {

    // reminder: mutexes are not reentrant and a coroutine should not suspend while holding a lock
    //  as it does not release it while suspended
    private val lock = Mutex()

    // internal state
    private var _state: CircuitBreakerState = Closed
    // TODO: watchout for lateinit if using internal events
    private lateinit var openStateStartTimeMark: ComparableTimeMark
    private lateinit var halfStateStartTimeMark: ComparableTimeMark

    override suspend fun currentState(): CircuitBreakerState = lock.withLock { _state }

    override suspend fun dispatch(event: CircuitBreakerReducerEvent): Unit = lock.withLock {
        // TODO: check for internal event
        /*if (openStateStartTimeMark.elapsedNow() >= config.waitDurationInOpenState) {
            transitionToHalfOpenState(0)
        }*/
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

            Open -> {} // no-op

            is HalfOpen -> {
                when (event) {
                    OPERATION_SUCCESS -> slidingWindow.recordSuccess()
                    OPERATION_FAILURE -> slidingWindow.recordFailure()
                }
                val nrOfCallsAttempted = state.nrOfCallsAttempted + 1
                if (config.maxWaitDurationInHalfOpenState != Duration.ZERO
                    // TODO: should this be an internal event and execute before dispatch?
                    && halfStateStartTimeMark.elapsedNow() >= config.maxWaitDurationInHalfOpenState) {
                    transitionToOpenState()
                } else {
                    if (nrOfCallsAttempted >= config.permittedNumberOfCallsInHalfOpenState) {
                        // check if the failure rate is still above or equal to the threshold
                        determineHalfOpenTransitionBasedOnFailureRate()
                    } else {
                        // maintain the HalfOpen state
                        transitionToHalfOpenState(nrOfCallsAttempted)
                    }
                }
            }
        }
    }

    private fun transitionToOpenState() {
        _state = Open
        openStateStartTimeMark = TestTimeSource().markNow()
    }

    private fun transitionToClosedState() {
        _state = Closed
    }

    private fun transitionToHalfOpenState(nrOfCallsAttempted: Int) {
        _state = HalfOpen(nrOfCallsAttempted)
        halfStateStartTimeMark = TestTimeSource().markNow()
    }

    /**
     * Determines the transition from the [HalfOpen] state based on the failure rate.
     * If the failure rate exceeds or equals the threshold, the circuit breaker transitions back to the [Open] state; otherwise, it transitions to the [Closed] state.
     */
    private fun determineHalfOpenTransitionBasedOnFailureRate() {
        if (slidingWindow.currentFailureRate() >= config.failureRateThreshold) {
            transitionToOpenState()
        } else {
            transitionToClosedState()
        }
    }

}
