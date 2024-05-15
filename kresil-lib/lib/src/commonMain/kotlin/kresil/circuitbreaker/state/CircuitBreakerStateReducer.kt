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
import kresil.circuitbreaker.slidingwindow.SlidingWindow
import kresil.circuitbreaker.state.CircuitBreakerState.CLOSED
import kresil.circuitbreaker.state.CircuitBreakerState.HALF_OPEN
import kresil.circuitbreaker.state.CircuitBreakerState.OPEN
import kresil.core.reducer.Reducer

// should work similarly to the useReducer in React
class CircuitBreakerStateReducer<T>(
    private val slidingWindow: SlidingWindow<T>,
    private val config: CircuitBreakerConfig,
) : Reducer<CircuitBreakerState, CircuitBreakerReducerEvent> {

    private val scope = CoroutineScope(Dispatchers.Default)
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
                    if (slidingWindow.currentFailureRate() < config.failureRateThreshold) {
                        transitionStateFrom(CLOSED to OPEN)
                    }
                    slidingWindow.recordFailure()
                }
            }

            OPEN -> when (event) {
                OPERATION_SUCCESS, OPERATION_FAILURE -> {
                    // no-op
                }
            }

            HALF_OPEN -> when (event) {
                OPERATION_SUCCESS, OPERATION_FAILURE -> {
                    nrOfCallsInHalfOpenState++
                    if (nrOfCallsInHalfOpenState >= config.permittedNumberOfCallsInHalfOpenState) {
                        transitionStateFrom(HALF_OPEN to CLOSED)
                    }
                }
            }
        }
    }

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

    private fun startOpenStateTimer() {
        // TODO: handle the case where there's no delay
        openStateTimerJob?.cancel()
        openStateTimerJob = scope.launch {
            delay(config.waitDurationInOpenState)
            lock.withLock {
                if (internalState == OPEN) {
                    transitionStateFrom(OPEN to HALF_OPEN)
                }
            }
        }
    }

    private fun startHalfOpenStateTimer() {
        halfOpenStateTimerJob?.cancel()
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
