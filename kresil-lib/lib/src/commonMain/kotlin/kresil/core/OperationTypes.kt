package kresil.core

/**
 * Represents a suspendable operation that accepts two arguments and produces a result.
 * @param A the type of the first argument
 * @param B the type of the second argument
 * @param R the type of the result
 */
typealias BiFunction<A, B, R> = suspend (A, B) -> R?

/**
 * Represents a suspendable operation that accepts one argument and produces a result.
 * @param A the type of the argument
 * @param R the type of the result
 */
typealias Function<A, R> = suspend (A) -> R?

/**
 * Represents a suspendable operation that accepts no arguments and produces a result.
 * @param R the type of the result
 */
typealias Supplier<R> = suspend () -> R?
