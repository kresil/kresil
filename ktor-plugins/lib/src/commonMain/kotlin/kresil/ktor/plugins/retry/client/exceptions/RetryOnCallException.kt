package kresil.ktor.plugins.retry.exceptions

/**
 * Represents an internal exception thrown when a retry is attempted because should retry on call predicate was met.
 */
internal class RetryOnCallException : Exception()
