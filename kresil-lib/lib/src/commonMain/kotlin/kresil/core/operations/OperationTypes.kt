package kresil.core.operations

/**
 * Represents a potentially suspendable operation that accepts two arguments and produces a result.
 * Based on Java's [BiFunction](https://docs.oracle.com/javase/8/docs/api/java/util/function/BiFunction.html)
 * interface.
 * @param A the type of the first argument
 * @param B the type of the second argument
 * @param R the type of the result
 */
typealias BiFunction<A, B, R> = suspend (A, B) -> R?

/**
 * Represents a potentially suspendable operation that accepts one argument and produces a result.
 * Based on Java's [Function](https://docs.oracle.com/javase/8/docs/api/java/util/function/Function.html) interface.
 * @param A the type of the argument
 * @param R the type of the result
 */
typealias Function<A, R> = suspend (A) -> R?

/**
 * Represents a potentially suspendable operation that accepts no arguments and produces a result.
 * Based on Java's [Supplier](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html) interface.
 * @param R the type of the result
 */
typealias Supplier<R> = suspend () -> R?
