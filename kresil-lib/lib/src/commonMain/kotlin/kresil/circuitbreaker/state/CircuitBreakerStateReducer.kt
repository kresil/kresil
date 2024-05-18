package kresil.circuitbreaker.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.state.CircuitBreakerReducerEvent.OPERATION_FAILURE
import kresil.circuitbreaker.state.CircuitBreakerReducerEvent.OPERATION_SUCCESS
import kresil.circuitbreaker.state.CircuitBreakerState.*
import kresil.core.reducer.Reducer
import kresil.core.slidingwindow.FailureRateSlidingWindow
import kotlin.time.Duration
import kresil.circuitbreaker.CircuitBreaker

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

    private val scope = CoroutineScope(Dispatchers.Default)
    // reminder: mutexes are not reentrant and a coroutine should not suspend while holding a lock
    //  as it does not release it while suspended
    private val lock = Mutex()

    // internal state
    private var internalState = CLOSED
    private var nrOfCallsInHalfOpenState = 0
    private var openStateTimerJob: Job? = null
    private var halfOpenStateTimerJob: Job? = null

    override suspend fun currentState() =
        lock.withLock { internalState }

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

            OPEN -> when (event) {
                OPERATION_SUCCESS, OPERATION_FAILURE -> {
                    // no-op
                }
            }

            HALF_OPEN -> when (event) {
                OPERATION_SUCCESS, OPERATION_FAILURE -> {
                    // TODO: this is incorrect, as failure or success needs to be recorded
                    //  missing halfOpen to Open if failure rate exceeds threshold
                    if (++nrOfCallsInHalfOpenState >= config.permittedNumberOfCallsInHalfOpenState) {
                        transitionStateFrom(HALF_OPEN to CLOSED)
                    }
                }
            }
        }
    }

    /**
     * Transitions the circuit breaker from one state to another, while performing the necessary side effects.
     *
     * **Note**: Since it alters the state, it should be called while holding the lock.
     */
    private fun transitionStateFrom(transition: Pair<CircuitBreakerState, CircuitBreakerState>) {
        when (transition) {
            CLOSED to OPEN, HALF_OPEN to OPEN -> transitionToOpenState()
            OPEN to HALF_OPEN -> transitionToHalfOpenState()
            HALF_OPEN to CLOSED -> transitionToClosedState()
            else -> throw IllegalStateException("Invalid transition in circuit breaker: $transition")
        }
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
     * If the [CircuitBreakerConfig.waitDurationInOpenState] is set to [Duration.ZERO], the transition is immediate.
     *
     * **Note**: Since it alters the state, it should be called while holding the lock.
     */
    private fun startOpenStateTimer() {
        openStateTimerJob?.cancel()
        if (config.waitDurationInOpenState == Duration.ZERO) {
            transitionStateFrom(OPEN to HALF_OPEN)
            return
        }
        openStateTimerJob = scope.launch {
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
     * If the [CircuitBreakerConfig.waitDurationInHalfOpenState] is set to [Duration.ZERO], the transition is immediate.
     *
     * **Note**: Since it alters the state, it should be called while holding the lock.
     */
    private fun startHalfOpenStateTimer() {
        halfOpenStateTimerJob?.cancel()
        if (config.waitDurationInHalfOpenState == Duration.ZERO) {
            transitionStateFrom(HALF_OPEN to CLOSED)
            return
        }
        halfOpenStateTimerJob = scope.launch {
            delay(config.waitDurationInHalfOpenState)
            lock.withLock {
                if (internalState == HALF_OPEN) {
                    transitionStateFrom(HALF_OPEN to CLOSED)
                }
            }
        }
    }

}
