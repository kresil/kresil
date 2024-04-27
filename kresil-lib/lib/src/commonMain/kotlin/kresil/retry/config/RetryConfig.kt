package kresil.retry.config

data class RetryConfig(
    val maxAttempts: Int,
    val retryIf: RetryPredicate,
    val retryOnResult: RetryOnResultPredicate,
    val delay: RetryDelayProvider,
) {
    val permittedRetryAttempts: Int = maxAttempts - 1
}
