package kresil.retry.exceptions

/**
 * Exception that is thrown when the maximum number of retries is exceeded either by previously retrying on error or on non-accepted result
 */
internal class MaxRetriesExceededException : IllegalStateException("Max retries exceeded")
