package kresil.circuitbreaker

import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.state.CircuitBreakerReducerEvent.*
import kresil.circuitbreaker.exceptions.CircuitBreakerOpenException
import kresil.circuitbreaker.slidingwindow.CountBasedSlidingWindow
import kresil.circuitbreaker.state.CircuitBreakerState.CLOSED
import kresil.circuitbreaker.state.CircuitBreakerState.HALF_OPEN
import kresil.circuitbreaker.state.CircuitBreakerState.OPEN
import kresil.circuitbreaker.state.CircuitBreakerStateReducer
import kresil.core.oper.Supplier

class CircuitBreaker(
    val config: CircuitBreakerConfig,
) { // TODO: needs to implement flow event listener

    private val stateReducer = CircuitBreakerStateReducer(
        slidingWindow = CountBasedSlidingWindow(config.slidingWindowSize),
        config = config,
    )

    // TODO: introduce all operation types here later (Supplier, Function, BiFunction)
    suspend fun <R> executeOperation(block: Supplier<R>): R =
        when (val observedState = stateReducer.currentState()) {
            OPEN -> {
                throw CircuitBreakerOpenException(observedState)
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

    private suspend fun <R> safeExecute(block: Supplier<R>): R =
        try {
            block()
        } catch (e: Throwable) {
            handleFailure(e)
        }

    private suspend fun <R> handleFailure(throwable: Throwable): R =
        when (val observedState = stateReducer.currentState()) {
            OPEN -> {
                throw CircuitBreakerOpenException(observedState)
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
