package kresil.core.callbacks

/**
 * Predicate to determine if an exception should be **considered as a failure**.
 * Depending on the mechanism context, this predicate can be used in different ways:
 * - **retry**: used to determine if a retry should be attempted;
 * - **circuit breaker**: used to determine if a failure should be recorded.
 */
typealias OnExceptionPredicate = (Throwable) -> Boolean

/**
 * Predicate to determine if a result should be **considered as a failure**.
 * Depending on the mechanism context, this predicate can be used in different ways:
 * - **retry**: used to determine if a retry should be attempted;
 * - **circuit breaker**: used to determine if a failure should be recorded.
 */
typealias OnResultPredicate = (result: Any?) -> Boolean

/**
 * Callback to map a result or an exception to a specific type and/or perform additional operations (e.g., logging, throwing an exception).
 */
typealias ResultMapper = (Any?, Throwable?) -> Any?
