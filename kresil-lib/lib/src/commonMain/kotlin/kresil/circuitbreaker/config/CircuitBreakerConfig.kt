package kresil.circuitbreaker.config

import kresil.core.callbacks.OnExceptionPredicate
import kresil.core.callbacks.OnResultPredicate
import kotlin.time.Duration

data class CircuitBreakerConfig(
    val failureRateThreshold: Double, // between 0.0 and 1.0
    val slidingWindowSize: Int,
    val minimumThroughput: Int,
    val permittedNumberOfCallsInHalfOpenState: Int,
    val waitDurationInOpenState: Duration,
    val recordFailurePredicate: OnExceptionPredicate,
    val recordSuccessAsFailurePredicate: OnResultPredicate,
    val ignoreFailurePredicate: OnExceptionPredicate,
    // TODO: add support for count and time based sliding windows
)
