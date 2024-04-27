package kresil.retry.config

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal typealias RetryDelayProvider = (attempt: Int, lastThrowable: Throwable?) -> Duration
internal typealias RetryPredicate = (Throwable) -> Boolean
internal typealias RetryOnResultPredicate = (Any?) -> Boolean

// TODO: revisit visibility concerns
class RetryConfigBuilder {

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        val DEFAULT_RETRY_PREDICATE: RetryPredicate = { true }
        val DEFAULT_RETRY_ON_RESULT_PREDICATE: RetryOnResultPredicate = { false }
    }

    init {
        exponentialDelay()
        retryIf { DEFAULT_RETRY_PREDICATE(it) }
        retryOnResult { DEFAULT_RETRY_ON_RESULT_PREDICATE(it) }
    }

    private lateinit var delay: RetryDelayProvider
    private lateinit var retryIf: RetryPredicate
    private lateinit var retryOnResult: RetryOnResultPredicate
    var maxAttempts: Int = DEFAULT_MAX_ATTEMPTS

    fun retryIf(predicate: RetryPredicate) {
        retryIf = predicate
    }

    fun retryOnResult(predicate: RetryOnResultPredicate) {
        retryOnResult = predicate
    }

    // Custom constant delay strategy
    fun constantDelay(duration: Duration) {
        requirePositiveDuration(duration, "Delay")
        delay = { _, _ -> duration }
    }

    // Custom exponential backoff delay strategy
    fun exponentialDelay(
        initialDelay: Duration = 500L.milliseconds,
        multiplier: Double = 2.0, // not using constant to be readable for the user
        maxDelay: Duration = 1.minutes
    ) {
        requirePositiveDuration(initialDelay, "Initial delay")
        require(multiplier > 1.0) { "Multiplier must be greater than 1" }
        val initialDelayMillis = initialDelay.inWholeMilliseconds
        val maxDelayMillis = maxDelay.inWholeMilliseconds
        require(initialDelayMillis < maxDelayMillis) { "Max delay must be greater than initial delay" }
        delay = { attempt, _ ->
            val nextDurationMillis = initialDelayMillis * multiplier.pow(attempt)
            nextDurationMillis.milliseconds.coerceAtMost(maxDelayMillis.milliseconds)
        }
    }

    fun customDelay(delayProvider: RetryDelayProvider) {
        delay = delayProvider
    }

    fun build() = RetryConfig(
        maxAttempts,
        retryIf,
        retryOnResult,
        delay
    )

    private inline fun requirePositiveDuration(duration: Duration, qualifier: String) {
        require(duration > Duration.ZERO) { "$qualifier duration must be greater than 0" }
    }

}
