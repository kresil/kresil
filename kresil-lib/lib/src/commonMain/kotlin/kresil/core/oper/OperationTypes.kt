package kresil.core.oper

/**
 * Represents a potentially suspendable operation that **accepts no arguments and produces a result**.
 * Based on Java's [Supplier](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html) functional interface.
 * @param Result the type of the result
 */
typealias Supplier<Result> = suspend () -> Result
/**
 * Represents a potentially suspendable operation that **accepts one argument and produces a result**.
 * Based on Java's [Function](https://docs.oracle.com/javase/8/docs/api/java/util/function/Function.html) functional interface.
 * @param Input the type of the argument
 * @param Result the type of the result
 */
typealias Function<Input, Result> = suspend (Input) -> Result

/**
 * Represents a potentially suspendable operation that **accepts two arguments and produces a result**.
 * Based on Java's [BiFunction](https://docs.oracle.com/javase/8/docs/api/java/util/function/BiFunction.html)
 * functional interface.
 * @param InputA the type of the first argument
 * @param InputB the type of the second argument
 * @param Result the type of the result
 */
typealias BiFunction<InputA, InputB, Result> = suspend (InputA, InputB) -> Result

