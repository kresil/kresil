package kresil.core.operations

/**
 * Represents a potentially suspendable operation that accepts two arguments and produces a result which can be null.
 * Based on Java's [BiFunction](https://docs.oracle.com/javase/8/docs/api/java/util/function/BiFunction.html)
 * functional interface.
 * See [BiFunction] for a non-nullable version of this operation.
 * @param InputA the type of the first argument
 * @param InputB the type of the second argument
 * @param Result the type of the result
 */
typealias NBiFunction<InputA, InputB, Result> = suspend (InputA, InputB) -> Result?

/**
 * Represents a potentially suspendable operation that accepts one argument and produces a result which can be null.
 * Based on Java's [Function](https://docs.oracle.com/javase/8/docs/api/java/util/function/Function.html) functional interface.
 * See [Function] for a non-nullable version of this operation.
 * @param Input the type of the argument
 * @param Result the type of the result
 */
typealias NFunction<Input, Result> = suspend (Input) -> Result?

/**
 * Represents a potentially suspendable operation that accepts no arguments and produces a result which can be null.
 * Based on Java's [Supplier](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html) functional interface.
 * See [Supplier] for a non-nullable version of this operation.
 * @param Result the type of the result
 */
typealias NSupplier<Result> = suspend () -> Result?

/**
 * Represents a potentially suspendable operation that accepts two arguments and produces a result.
 * Based on Java's [BiFunction](https://docs.oracle.com/javase/8/docs/api/java/util/function/BiFunction.html)
 * functional interface.
 * See [NBiFunction] for a nullable version of this operation.
 * @param InputA the type of the first argument
 * @param InputB the type of the second argument
 * @param Result the type of the result
 */
typealias BiFunction<InputA, InputB, Result> = suspend (InputA, InputB) -> Result

/**
 * Represents a potentially suspendable operation that accepts one argument and produces a result.
 * Based on Java's [Function](https://docs.oracle.com/javase/8/docs/api/java/util/function/Function.html) functional interface.
 * See [NFunction] for a nullable version of this operation.
 * @param Input the type of the argument
 * @param Result the type of the result
 */
typealias Function<Input, Result> = suspend (Input) -> Result

/**
 * Represents a potentially suspendable operation that accepts no arguments and produces a result.
 * Based on Java's [Supplier](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html) functional interface.
 * See [NSupplier] for a nullable version of this operation.
 * @param Result the type of the result
 */
typealias Supplier<Result> = suspend () -> Result
