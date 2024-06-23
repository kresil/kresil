package kresil.core.delay

import kotlinx.coroutines.test.runTest
import kresil.core.delay.provider.CtxDelayProvider
import kresil.core.delay.provider.DelayProvider
import kresil.core.delay.strategy.DelayStrategyOptions.constant
import kresil.core.delay.strategy.DelayStrategyOptions.customProvider
import kresil.core.delay.strategy.DelayStrategyOptions.exponential
import kresil.core.delay.strategy.DelayStrategyOptions.linear
import kresil.core.delay.strategy.DelayStrategyOptions.noDelay
import kresil.extensions.randomTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DelayStrategyTests {

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
    fun validateConstantDelayStrategyWithJitter() = runTest {
        // given: a constant delay duration
        val delay = 500.milliseconds
        val randomizationFactor = 0.1

        // and: expected values
        val expectedValues = List(10) { jitterDurationRange(delay, randomizationFactor) }

        // when: the delay duration is requested
        val constantDelay = constant(delay, randomizationFactor)

        // then: delay duration should be in the expected range
        expectedValues.forEachIndexed { index, expectedDelay ->
            val delayDuration = constantDelay(index + 1)
            assertTrue(delayDuration in expectedDelay)
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
        val linearDelay = linear(initialDelay, maxDelay = maxDelay)

        // then: the delay duration should be correct
        map.forEach { (attempt, expectedDelay) ->
            assertEquals(expectedDelay, linearDelay(attempt))
        }
    }

    @Test
    fun validateLinearDelayStrategyWithNonDefaultMultiplier() = runTest {
        // given: linear delay strategy parameters
        val initialDelay = 100.milliseconds
        val multiplier = 2.0

        // and: expected values
        val map = mapOf(
            1 to 100.milliseconds,
            2 to 300.milliseconds,
            3 to 500.milliseconds,
            4 to 700.milliseconds,
            5 to 900.milliseconds,
        )

        // when: the delay duration is calculated
        val linearDelay = linear(initialDelay, multiplier = multiplier)

        // then: the delay duration should be correct
        map.forEach { (attempt, expectedDelay) ->
            assertEquals(expectedDelay, linearDelay(attempt))
        }
    }

    @Test
    fun validateLinearDelayStrategyWithJitter() = runTest {
        // given: linear delay strategy parameters
        val initialDelay = 500.milliseconds
        val maxDelay = 1.minutes
        val randomizationFactor = 0.1

        // and: expected values
        val expectedValues = mapOf(
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
        ).mapValues {
            jitterDurationRange(it.value, randomizationFactor)
        }

        // when: the delay duration is calculated
        val linearDelay = linear(initialDelay, maxDelay = maxDelay, randomizationFactor = randomizationFactor)

        // then: delay duration should be in the expected range
        expectedValues.forEach { (attempt, expectedDelay) ->
            val delayDuration = linearDelay(attempt)
            assertTrue(delayDuration in expectedDelay)
        }
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
        val exponentialDelay = exponential(initialDelay, maxDelay, multiplier)

        // then: the delay duration should be correct
        map.forEach { (attempt, expectedDelay) ->
            assertEquals(expectedDelay, exponentialDelay(attempt))
        }
    }

    @Test
    fun validateExponentialDelayStrategyWithNonDefaultMultiplier() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = 100.milliseconds
        val multiplier = 3.0

        // and: expected values
        val map = mapOf(
            1 to 100.milliseconds,
            2 to 300.milliseconds,
            3 to 900.milliseconds,
            4 to 2700.milliseconds,
            5 to 8100.milliseconds,
        )

        // when: the delay duration is calculated
        val exponentialDelay = exponential(initialDelay, multiplier = multiplier)

        // then: the delay duration should be correct
        map.forEach { (attempt, expectedDelay) ->
            assertEquals(expectedDelay, exponentialDelay(attempt))
        }
    }

    @Test
    fun validateExponentialDelayStrategyWithJitter() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = 500.milliseconds
        val multiplier = 2.0
        val maxDelay = 1.minutes
        val randomizationFactor = 0.25

        // and: expected values
        val expectedValues = mapOf(
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
        ).mapValues {
            jitterDurationRange(it.value, randomizationFactor)
        }

        // when: the delay duration is calculated
        val exponentialDelay = exponential(initialDelay, maxDelay, multiplier, randomizationFactor)

        // then: delay duration should be in the expected range
        expectedValues.forEach { (attempt, expectedDelay) ->
            val delayDuration = exponentialDelay(attempt)
            assertTrue(delayDuration in expectedDelay)
        }

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
        val delayDuration = customProvider(customProvider)

        repeat(10) {
            // then: the state should be updated
            assertEquals(it, customProvider.delayProviderCounter)

            // and: the delay duration should be the same as the custom provider
            assertEquals(200.milliseconds * (it + 1), delayDuration(it + 1))
        }

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

    private fun jitterDurationRange(duration: Duration, factor: Double): ClosedRange<Duration> {
        val jitter = duration * factor
        return (duration - jitter)..(duration + jitter)
    }
}
