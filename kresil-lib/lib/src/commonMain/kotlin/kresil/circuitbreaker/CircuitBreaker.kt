package kresil.circuitbreaker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.exceptions.CircuitBreakerOpenException
import kresil.circuitbreaker.slidingwindow.CountBasedSlidingWindow
import kresil.circuitbreaker.state.CircuitBreakerState
import kresil.circuitbreaker.state.CircuitBreakerState.CLOSED
import kresil.circuitbreaker.state.CircuitBreakerState.HALF_OPEN
import kresil.circuitbreaker.state.CircuitBreakerState.OPEN
import kresil.core.oper.Supplier
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

class CircuitBreaker(
    val config: CircuitBreakerConfig,
) { // TODO: needs to implement flow event listener

    // TODO: cannot use reentrantLock here (working with coroutines not threads)
    // - also mutex is not reentrant (i.e., cannot be locked multiple times by the same coroutine)
    // - also do not suspend while holding a lock (which could hold the lock indefinitely)
    private val lock = Mutex()

    // state
    private val slidingWindow = CountBasedSlidingWindow(config.slidingWindowSize)
    private var state = CLOSED
    private var failureCountInHalfOpenState = 0

    // observables
    private suspend fun currentState(): CircuitBreakerState = lock.withLock { state }

    // TODO: introduce all operation types here later (Supplier, Function, BiFunction)
    suspend fun <R> executeOperation(block: Supplier<R>): Result<R> {
        lock.lock()
        when (state) {
            CLOSED -> {
                lock.unlock()
                // Circuit breaker is closed, execute the action block
                // without lock possession (possible indefinite wait)
                return safeExecute(block)
            }

            OPEN -> {
                // Circuit breaker is open, disallowing subsequent calls
                lock.unlock()
                return failure(CircuitBreakerOpenException(state))
            }

            HALF_OPEN -> {
                // Circuit breaker is in half-open state, allowing limited calls
                if (failureCountInHalfOpenState >= config.permittedNumberOfCallsInHalfOpenState) {
                    // Too many failures, transitioning back to OPEN state
                    state = OPEN
                    lock.unlock()
                    return failure(CircuitBreakerOpenException(state))
                } else {
                    lock.unlock()
                    // Execute the action block without lock possession
                    // (possible indefinite wait)
                    return safeExecute(block)
                }
            }
        }
    }

    private suspend fun <R> safeExecute(block: Supplier<R>): Result<R> =
        try {
            val result = block()
            slidingWindow.recordSuccess()
            success(result)
        } catch (e: Throwable) {
            handleFailure(e)
        }

    private suspend fun <R> handleFailure(throwable: Throwable): Result<R> = lock.withLock {
        return when (state) {
            CLOSED -> {
                // Circuit breaker is closed, record failure
                slidingWindow.recordFailure()
                if (slidingWindow.currentFailureRate() >= config.failureRateThreshold) {
                    // Too many failures recorded, transitioning to OPEN state
                    state = OPEN
                    failure(CircuitBreakerOpenException(state))
                } else {
                    failure(throwable)
                }
            }

            OPEN -> {
                // Circuit breaker is already open, propagate the exception
                failure(CircuitBreakerOpenException(state))
            }

            HALF_OPEN -> {
                // Circuit breaker is in half-open state, increment failure count
                failureCountInHalfOpenState++
                if (failureCountInHalfOpenState >= config.permittedNumberOfCallsInHalfOpenState) {
                    // Too many failures, transitioning back to OPEN state
                    state = OPEN
                    failureCountInHalfOpenState = 0
                    failure(CircuitBreakerOpenException(state))
                } else {
                    failure(throwable)
                }
            }
        }
    }

    suspend fun reset() = lock.withLock {
        state = CLOSED
        failureCountInHalfOpenState = 0
        slidingWindow.clear()
    }
}
