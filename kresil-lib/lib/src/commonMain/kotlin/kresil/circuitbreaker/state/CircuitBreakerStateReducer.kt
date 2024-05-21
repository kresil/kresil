package kresil.circuitbreaker.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.state.CircuitBreakerReducerEvent.OPERATION_FAILURE
import kresil.circuitbreaker.state.CircuitBreakerReducerEvent.OPERATION_SUCCESS
import kresil.circuitbreaker.state.CircuitBreakerState.CLOSED
import kresil.circuitbreaker.state.CircuitBreakerState.HALF_OPEN
import kresil.circuitbreaker.state.CircuitBreakerState.OPEN
import kresil.core.reducer.Reducer
import kresil.core.slidingwindow.FailureRateSlidingWindow
import kotlin.time.Duration

/**
 * Represents a transition between two states in a [CircuitBreaker], from left to right.
 */
private typealias StateTransition = Pair<CircuitBreakerState, CircuitBreakerState>

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
) : Reducer<CircuitBreakerReducerState, CircuitBreakerReducerEvent> {

    private val scope = CoroutineScope(Dispatchers.Default)

    // reminder: mutexes are not reentrant and a coroutine should not suspend while holding a lock
    //  as it does not release it while suspended
    private val lock = Mutex()

    // internal state
    private var internalState = CLOSED
    private var nrOfCallsInHalfOpenState = 0
    private var openStateTimerJob: Job? = null
    private var halfOpenStateTimerJob: Job? = null

    override suspend fun currentState(): CircuitBreakerReducerState =
        lock.withLock { CircuitBreakerReducerState(state = internalState, nrOfCallsInHalfOpenState) }

    override suspend fun dispatch(event: CircuitBreakerReducerEvent) = lock.withLock {
        when (internalState) {
            CLOSED -> when (event) {
                OPERATION_SUCCESS -> slidingWindow.recordSuccess()
                OPERATION_FAILURE -> {
                    slidingWindow.recordFailure()
                    if (slidingWindow.currentFailureRate() >= config.failureRateThreshold) {
                        transitionStateFrom(CLOSED to OPEN)
                    }
                }
            }

            OPEN -> {} // no-op

            HALF_OPEN -> {
                when (event) {
                    OPERATION_SUCCESS -> slidingWindow.recordSuccess()
                    OPERATION_FAILURE -> slidingWindow.recordFailure()
                }
                nrOfCallsInHalfOpenState++
                if (config.maxWaitDurationInHalfOpenState == Duration.ZERO) {
                    // is waiting indefinitely in the HALF_OPEN state until the permitted number of calls is reached
                    if (nrOfCallsInHalfOpenState >= config.permittedNumberOfCallsInHalfOpenState) {
                        // check if the failure rate is still above or equal to the threshold
                        determineHalfOpenTransitionBasedOnFailureRate()
                    }
                }
                // else the timer will handle the transition
            }
        }
    }

    /**
     * Transitions the circuit breaker from one state to another, while performing the necessary side effects.
     */
    private fun transitionStateFrom(transition: StateTransition) =
        when (transition) {
            CLOSED to OPEN, HALF_OPEN to OPEN -> transitionToOpenState()
            OPEN to HALF_OPEN -> transitionToHalfOpenState()
            HALF_OPEN to CLOSED -> transitionToClosedState()
            else -> throw IllegalStateException("Invalid transition in circuit breaker: $transition")
        }

    private fun transitionToClosedState() {
        internalState = CLOSED
    }

    private fun transitionToOpenState() {
        internalState = OPEN
        startOpenStateTimer()
    }

    private fun transitionToHalfOpenState() {
        internalState = HALF_OPEN
        nrOfCallsInHalfOpenState = 0
        startHalfOpenStateTimer()
    }

    /**
     * Starts a timer that transitions the circuit breaker from the [OPEN] state to the [HALF_OPEN] state,
     * if the timer is not cancelled before it expires or the state changes in the meantime.
     */
    private fun startOpenStateTimer() {
        openStateTimerJob?.cancel()
        openStateTimerJob = scope.launch { // another coroutine is launched here
            delay(config.waitDurationInOpenState)
            lock.withLock {
                if (internalState == OPEN) {
                    transitionStateFrom(OPEN to HALF_OPEN)
                }
            }
        }
    }

    /**
     * Starts a timer that transitions the circuit breaker from the [HALF_OPEN] state to the [CLOSED] state,
     * if the timer is not cancelled before it expires or the state changes in the meantime.
     * If the maximum duration in the [HALF_OPEN] state is set to 0, the circuit breaker will wait indefinitely
     * until the permitted number of calls is reached.
     */
    private fun startHalfOpenStateTimer() {
        halfOpenStateTimerJob?.cancel()
        // if maxWaitDurationInHalfOpenState is 0, the circuit breaker will wait indefinitely in the HALF_OPEN state
        // until the permitted number of calls is reached
        if (config.maxWaitDurationInHalfOpenState == Duration.ZERO)
            return
        // TODO: do not launch a coroutine here, save a timestamp instead
        halfOpenStateTimerJob = scope.launch { // another coroutine is launched here
            delay(config.maxWaitDurationInHalfOpenState)
            lock.withLock {
                determineHalfOpenTransitionBasedOnFailureRate()
            }
        }
    }

    /**
     * Determines the transition from the [HALF_OPEN] state based on the failure rate.
     * If the failure rate exceeds or equals the threshold, the circuit breaker transitions to the [OPEN] state;
     * otherwise, it transitions to the [CLOSED] state.
     */
    private fun determineHalfOpenTransitionBasedOnFailureRate() {
        if (slidingWindow.currentFailureRate() >= config.failureRateThreshold) {
            transitionStateFrom(HALF_OPEN to OPEN)
        } else {
            transitionStateFrom(HALF_OPEN to CLOSED)
        }
    }

}
