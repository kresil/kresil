package kresil.core.callbacks

/**
 * Predicate to determine if an exception should be **considered as a failure**.
 * Depending on the context, this predicate can be used in different ways:
 * - **retry**: used to determine if a retry should be attempted;
 * - **circuit breaker**: used to determine if a failure should be recorded.
 */
typealias OnExceptionPredicate = (Throwable) -> Boolean

/**
 * Predicate to determine if a result should be **considered as a failure**.
 * Depending on the context, this predicate can be used in different ways:
 * - **retry**: used to determine if a retry should be attempted;
 * - **circuit breaker**: used to determine if a failure should be recorded.
 */
typealias OnResultPredicate = (result: Any?) -> Boolean

/**
 * Callback to handle caught exceptions.
 * Can be used to stop error propagation or add additional logging.
 */
typealias ExceptionHandler = (throwable: Throwable) -> Unit
