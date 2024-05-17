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

class CircuitBreaker(
    val config: CircuitBreakerConfig = defaultCircuitBreakerConfig(),
) { // TODO: needs to implement flow event listener

    private val slidingWindow = CountBasedSlidingWindow(
        capacity = config.slidingWindowSize,
        minimumThroughput = config.minimumThroughput,
    )
    private val stateReducer = CircuitBreakerStateReducer(slidingWindow, config)

    suspend fun currentState() = stateReducer.currentState()

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

    private suspend fun <R> safeExecute(block: suspend () -> R): R =
        try {
            block()
        } catch (e: Throwable) {
            handleFailure(e)
        }

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
