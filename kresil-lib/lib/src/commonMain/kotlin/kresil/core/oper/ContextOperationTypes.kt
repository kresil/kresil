package kresil.core.oper

/**
 * Similiar to [Supplier] but additionally accepts a context as the first argument.
 * @param Context the type of the context
 * @param Result the type of the result
 */
typealias CtxSupplier<Context, Result> = suspend (Context) -> Result
/**
 * Similiar to [Function] but additionally accepts a context as the first argument.
 * @param Context the type of the context
 * @param Input the type of the argument
 * @param Result the type of the result
 */
typealias CtxFunction<Context, Input, Result> = suspend (Context, Input) -> Result

/**
 * Similiar to [BiFunction] but additionally accepts a context as the first argument.
 * @param Context the type of the context
 * @param InputA the type of the first argument
 * @param InputB the type of the second argument
 * @param Result the type of the result
 */
typealias CtxBiFunction<Context, InputA, InputB, Result> = suspend (Context, InputA, InputB) -> Result
