package kresil.core.delay

import kotlinx.coroutines.test.runTest
import kresil.core.delay.DelayStrategyOptions.constant
import kresil.core.delay.DelayStrategyOptions.exponential
import kresil.core.delay.DelayStrategyOptions.linear
import kresil.core.delay.DelayStrategyOptions.noDelay
import kresil.core.delay.DelayStrategyOptions.validateConstantDelayParams
import kresil.core.delay.DelayStrategyOptions.validateExponentialDelayParams
import kresil.core.delay.DelayStrategyOptions.validateLinearDelayParams
import kresil.core.delay.provider.CtxDelayProvider
import kresil.core.delay.provider.DelayProvider
import kresil.extensions.randomTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DelayStrategyOptionsTest {

    @Test
    fun validateNoDelayStrategy() = runTest {
        // given: an attempt number
        val attempt = 50 randomTo 100

        // when: the delay duration is requested
        val noDelay = noDelay()
        val delayDuration = noDelay(attempt)

        // then: the delay duration should be zero
        assertEquals(ZERO, delayDuration)

        // when: the delay duration is requested again
        val attempt2 = 100 randomTo 1000
        val delayDurationSecond = noDelay(attempt2)

        // then: the delay duration should be zero, as the strategy does not consider the attempt number
        assertEquals(ZERO, delayDurationSecond)
    }

    @Test
    fun validateConstantDelayStrategy() = runTest {
        // given: a constant delay duration
        val delay = 500.milliseconds

        // and: an attempt number
        val attempt = 50 randomTo 1000

        // when: the delay duration is requested
        val constantDelay = constant(delay)
        val delayDuration = constantDelay(attempt)

        // then: the delay duration should be the same as the constant delay
        assertEquals(delay, delayDuration)

        // when: the delay duration is requested again
        val attempt2 = 100 randomTo 1000
        val delayDurationSecond = constantDelay(attempt2)

        // then: the delay duration should be the same as the constant delay, as the strategy does not consider the attempt number
        assertEquals(delay, delayDurationSecond)
    }

    @Test
    fun constantDelayStrategyWithInvalidDelay() = runTest {
        // given: an invalid constant delay duration
        val delay = (-500).milliseconds

        // when: the delay duration is calculated
        // then: an exception should be thrown
        assertFailsWith<IllegalArgumentException> {
            validateConstantDelayParams(delay)
        }
    }

    @Test
    fun validateLinearDelayStrategy() = runTest {
        // given: linear delay strategy parameters
        val initialDelay = 500.milliseconds
        val maxDelay = 1.minutes

        // and: expected values
        val map = mapOf(
            1 to 500.milliseconds,
            2 to 1.seconds,
            3 to 1.5.seconds,
            4 to 2.seconds,
            5 to 2.5.seconds,
            6 to 3.seconds,
            7 to 3.5.seconds,
            8 to 4.seconds,
            9 to 4.5.seconds,
            10 to 5.seconds
        )
        // when: the delay duration is calculated
        val linearDelay = linear(initialDelay, maxDelay)

        // then: delay duration should be correct
        map.forEach { (attempt, expectedDelay) ->
            assertEquals(expectedDelay, linearDelay(attempt))
        }
    }

    @Test
    fun linearDelayStrategyWithInvalidInitialDelay() = runTest {
        // given: linear delay strategy parameters
        val initialDelay = ZERO
        val maxDelay = 1.minutes

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            validateLinearDelayParams(initialDelay, maxDelay)
        }

        // and: the exception message should be correct
        assertEquals("Initial delay duration must be greater than zero", exception.message)
    }

    @Test
    fun linearDelayStrategyWithInvalidMaxDelay() = runTest {
        // given: linear delay strategy parameters
        val initialDelay = 500.milliseconds
        val maxDelay = (-1).minutes

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            validateLinearDelayParams(initialDelay, maxDelay)
        }

        // and: the exception message should be correct
        assertEquals("Max delay duration must be greater than initial delay", exception.message)
    }

    @Test
    fun validateExponentialDelayStrategy() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = 500.milliseconds
        val multiplier = 2.0
        val maxDelay = 1.minutes

        // and: expected values
        val map = mapOf(
            1 to 500.milliseconds,
            2 to 1.seconds,
            3 to 2.seconds,
            4 to 4.seconds,
            5 to 8.seconds,
            6 to 16.seconds,
            7 to 32.seconds,
            8 to 1.minutes,
            9 to 1.minutes,
            10 to 1.minutes
        )

        // when: the delay duration is calculated
        val exponentialDelay = exponential(initialDelay, multiplier, maxDelay)

        // then: delay duration should be correct
        map.forEach { (attempt, expectedDelay) ->
            assertEquals(expectedDelay, exponentialDelay(attempt))
        }
    }

    @Test
    fun exponentialDelayStrategyWithInvalidInitialDelay() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = ZERO
        val multiplier = 2.0
        val maxDelay = 1.minutes

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            validateExponentialDelayParams(initialDelay, multiplier, maxDelay)
        }

        // and: the exception message should be correct
        assertEquals("Initial delay duration must be greater than zero", exception.message)
    }

    @Test
    fun exponentialDelayStrategyWithInvalidMultiplier() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = 500.milliseconds
        val multiplier = 0.0
        val maxDelay = 1.minutes

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            validateExponentialDelayParams(initialDelay, multiplier, maxDelay)
        }

        // and: the exception message should be correct
        assertEquals("Multiplier must be greater than 1", exception.message)
    }

    @Test
    fun exponentialDelayStrategyWithInvalidMaxDelay() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = 500.milliseconds
        val multiplier = 2.0
        val maxDelay = (-1).minutes

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            validateExponentialDelayParams(initialDelay, multiplier, maxDelay)
        }

        // and: the exception message should be correct
        assertEquals("Max delay duration must be greater than initial delay", exception.message)
    }

    @Test
    fun validateCustomProviderDelayStrategy() = runTest {
        // given: custom delay strategy parameters
        val customProvider = object : DelayProvider {
            // state
            var delayProviderCounter = 0
                private set

            override suspend fun delay(attempt: Int): Duration {
                delayProviderCounter++
                return 200.milliseconds * attempt
            }
        }

        // when: the delay duration is calculated
        val delayDuration = DelayStrategyOptions.customProvider(customProvider)

        repeat(10) {
            // then: the state should be updated
            assertEquals(it, customProvider.delayProviderCounter)

            // and: the delay duration should be the same as the custom provider
            assertEquals(200.milliseconds * (it + 1), delayDuration(it + 1))
        }

    }

    fun <TContext> customProvider(provider: CtxDelayProvider<TContext>): CtxDelayStrategy<TContext> =
        { attempt, context ->
            provider.delay(attempt, context)
        }

    @Test
    fun validateCustomProviderCtxDelayStrategy() = runTest {
        // given: a context
        data class Context(val value: Int)

        // and: custom delay strategy parameters
        val customProvider = object : CtxDelayProvider<Context> {
            // state
            var delayProviderCounter = 0
                private set

            override suspend fun delay(attempt: Int, context: Context): Duration {
                delayProviderCounter++
                return 200.milliseconds * context.value
            }
        }

        // when: the delay duration is calculated
        val delayDuration = customProvider(customProvider)

        repeat(10) {
            // then: the state should be updated
            assertEquals(it, customProvider.delayProviderCounter)

            // and: the context should be updated
            assertEquals(200.milliseconds * (it + 1), delayDuration(it + 1, Context(it + 1)))
        }
    }
}
