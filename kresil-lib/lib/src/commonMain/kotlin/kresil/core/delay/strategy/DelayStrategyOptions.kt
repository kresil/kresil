package kresil.core.delay.strategy

import kresil.core.delay.provider.CtxDelayProvider
import kresil.core.delay.provider.DelayProvider
import kresil.core.delay.requireNonNegative
import kresil.core.delay.requirePositive
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Represents all the options available for configuring a [DelayStrategy].
 * @see [noDelay]
 * @see [linear]
 * @see [constant]
 * @see [exponential]
 * @see [customProvider]
 */
internal object DelayStrategyOptions {

    /**
     * A delay strategy that has no delay between attempts.
     * Attempts are immediate and do not use any custom [DelayProvider].
     */
    fun noDelay(): DelayStrategy = { _ -> Duration.ZERO }

    /**
     * A delay strategy that uses a constant backoff algorithm to calculate the next delay duration.
     * The algorithm is based on the formula:
     * - `delay + jitter`
     *
     * Example:
     * ```
     * constant(500.milliseconds)
     * // Delay between attempts will be 500ms
     * constant(500.milliseconds, 0.1)
     * // Delay between attempts will be something like:
     * // [495ms, 513ms, 502ms, 507ms, 499ms, ...]
     *
     * ```
     * **Note**:
     * - Because the jitter calculation is based on the newly calculated delay, the new delay could be less than the previous value.
     * @param delay The constant delay between attempts.
     * @param randomizationFactor the randomization factor to add randomness to the calculated delay (e.g., 0.1 for +/-10%).
     */
    fun constant(
        delay: Duration,
        randomizationFactor: Double = 0.0,
    ): DelayStrategy {
        validateConstantDelayParams(delay, randomizationFactor)
        return { _ ->
            applyJitter(delay, randomizationFactor)
        }
    }

    /**
     * A delay strategy that uses a linear backoff algorithm to calculate the next delay duration.
     * The algorithm is based on the formula:
     * - `initialDelay + (initialDelay * (attempt - 1) * multiplier) + jitter`,
     * where `attempt` is the current delay attempt which starts at **1**.
     *
     * Example:
     * ```
     * linear(500.milliseconds, 1.0, 4.seconds)
     * // Delay between attempts will be as follows:
     * // [500ms, 1s, 1.5s, 2s, 2.5s, 3s, 3.5s, 4s, 4s, 4s, ...]
     * linear(500.milliseconds, 1.0, 4.seconds, 0.1)
     * // Delay between attempts will be something like:
     * // [450ms, 1.1s, 1.4s, 2.2s, 2.3s, 3.1s, 3.4s, 4s, 4s, 4s, ...]
     * ```
     * **Note**:
     * - Because the jitter calculation is based on the newly calculated delay, the new delay could be less than the previous value.
     * @param initialDelay The initial delay before the first attempt.
     * @param multiplier The multiplier to increase the delay between attempts.
     * @param maxDelay The maximum delay between attempts. Used as a safety net to prevent infinite delays.
     * @param randomizationFactor the randomization factor to add randomness to the calculated delay (e.g., 0.1 for +/-10%).
     */
    fun linear(
        initialDelay: Duration,
        multiplier: Double = 1.0,
        maxDelay: Duration = Duration.INFINITE,
        randomizationFactor: Double = 0.0,
    ): DelayStrategy {
        validateLinearDelayParams(initialDelay, multiplier, maxDelay, randomizationFactor)
        return { attempt ->
            val baseDuration = initialDelay + (initialDelay * (attempt - 1) * multiplier)
            val jitteredDuration = applyJitter(baseDuration, randomizationFactor)
            jitteredDuration.coerceAtMost(maxDelay)
        }
    }

    /**
     * A delay strategy that uses the exponential backoff algorithm to calculate the next delay duration.
     * The algorithm is based on the formula:
     * - `(initialDelay * multiplier^(attempt - 1)) + jitter`,
     * where `attempt` is the current delay attempt which starts at **1**.
     *
     * Example:
     * ```
     * exponential(500.milliseconds, 2.0, 1.minutes)
     * // Delay between transitions will be as follows:
     * // [500ms, 1s, 2s, 4s, 8s, 16s, 32s, 1m, 1m, 1m, ...]
     * exponential(500.milliseconds, 2.0, 1.minutes, 0.1)
     * // Delay between transitions will be something like:
     * // [450ms, 1.1s, 2.2s, 3.9s, 8.1s, 15.8s, 32.3s, 1m, 1m, 1m, ...]
     * ```
     *
     * **Note**:
     * - Because the jitter calculation is based on the newly calculated delay, the new delay could be less than the previous value.
     * @param initialDelay The initial delay before the first attempt.
     * @param maxDelay The maximum delay between attempts. Used as a safety net to prevent infinite delays.
     * @param multiplier The multiplier to increase the delay between attempts.
     * @param randomizationFactor the randomization factor to add randomness to the calculated delay (e.g., 0.1 for +/-10%).
     */
    fun exponential(
        initialDelay: Duration,
        maxDelay: Duration = Duration.INFINITE,
        multiplier: Double = 2.0,
        randomizationFactor: Double = 0.0,
    ): DelayStrategy {
        validateExponentialDelayParams(initialDelay, multiplier, maxDelay, randomizationFactor)
        return { attempt ->
            val baseDuration = initialDelay * multiplier.pow(attempt - 1)
            val jitteredDuration = applyJitter(baseDuration, randomizationFactor)
            jitteredDuration.coerceAtMost(maxDelay)
        }
    }

    /**
     * A delay strategy that uses a custom delay provider, which extends the behaviour of a [DelayStrategy],
     * by allowing external control of the delay execution with optional state.
     */
    fun customProvider(provider: DelayProvider): DelayStrategy = { attempt ->
        provider.delay(attempt)
    }

    /**
     * A delay strategy that uses a custom delay provider,
     * which extends the behaviour of a [CtxDelayStrategy], by allowing external control of the delay execution with optional state.
     * @param provider The custom delay provider to use.
     * @see [CtxDelayProvider]
     */
    fun <TContext> customProvider(provider: CtxDelayProvider<TContext>): CtxDelayStrategy<TContext> =
        { attempt, context ->
            provider.delay(attempt, context)
        }

    /**
     * Converts a [DelayStrategy] into a [CtxDelayStrategy] using [Unit] as the context.
     * This is useful when no additional context is needed, allowing the caller to avoid providing a [Unit] context.
     * It maintains compatibility with the more general [CtxDelayStrategy] type.
     * @see [toEmptyCtxDelayStrategy]
     */
    fun DelayStrategy.toEmptyCtxDelayStrategy(): CtxDelayStrategy<Unit> = { attempt, _ ->
        this(attempt)
    }

    /**
     * Applies jitter to a given duration based on a randomization factor.
     *
     * Example:
     * - **Base Delay**: 1000ms
     * - **Randomization factor**: 0.1 (10%)
     *
     * Then:
     * - **Jittered delay**: 900ms to 1100ms
     *
     * **Note**: Calling this function with a randomization factor of 0 will return the base delay.
     * @param baseDelay The original delay duration.
     * @param randomizationFactor The factor to randomize the delay.
     * @return The jittered delay duration.
     */
    private fun applyJitter(baseDelay: Duration, randomizationFactor: Double): Duration {
        if (randomizationFactor == 0.0) return baseDelay
        val delta = randomizationFactor * baseDelay.inWholeMilliseconds
        val jitteredValue = baseDelay.inWholeMilliseconds + Random.nextDouble(-delta, delta)
        return jitteredValue.milliseconds
    }

    /**
     * Validates the [constant] delay parameters.
     */
    @Throws(IllegalArgumentException::class)
    private fun validateConstantDelayParams(
        delay: Duration,
        randomizationFactor: Double,
    ) {
        delay.requireNonNegative("Constant delay")
        require(randomizationFactor in 0.0..1.0) { "Randomization factor must be between 0 and 1" }
    }

    /**
     * Validates the [linear] delay parameters.
     */
    @Throws(IllegalArgumentException::class)
    private fun validateLinearDelayParams(
        initialDelay: Duration,
        multiplier: Double,
        maxDelay: Duration,
        randomizationFactor: Double,
    ) {
        initialDelay.requirePositive("Initial delay")
        require(multiplier > 0.0) { "Multiplier must be greater than 0" }
        require(initialDelay < maxDelay) { "Max delay duration must be greater than initial delay" }
        require(randomizationFactor in 0.0..1.0) { "Randomization factor must be between 0 and 1" }
    }

    /**
     * Validates the [exponential] delay parameters.
     */
    @Throws(IllegalArgumentException::class)
    private fun validateExponentialDelayParams(
        initialDelay: Duration,
        multiplier: Double,
        maxDelay: Duration,
        randomizationFactor: Double,
    ) {
        initialDelay.requirePositive("Initial delay")
        require(multiplier > 1.0) { "Multiplier must be greater than 1" }
        require(initialDelay < maxDelay) { "Max delay duration must be greater than initial delay" }
        require(randomizationFactor in 0.0..1.0) { "Randomization factor must be between 0 and 1" }
    }

}
