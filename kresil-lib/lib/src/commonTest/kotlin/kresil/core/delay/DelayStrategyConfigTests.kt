package kresil.core.delay

import kotlinx.coroutines.test.runTest
import kresil.core.delay.strategy.DelayStrategyOptions.constant
import kresil.core.delay.strategy.DelayStrategyOptions.exponential
import kresil.core.delay.strategy.DelayStrategyOptions.linear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class DelayStrategyConfigTests {

    @Test
    fun constantDelayStrategyWithInvalidDelay() = runTest {
        // given: an invalid constant delay duration
        val delay = (-500).milliseconds

        // when: the delay duration is calculated
        // then: an exception should be thrown
        assertFailsWith<IllegalArgumentException> {
            constant(delay)
        }
    }

    @Test
    fun constantDelayStrategyWithInvalidRandomizationFactor() = runTest {
        // given: constant delay strategy parameters
        val delay = 500.milliseconds
        val randomizationFactor = 1.1

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            constant(delay, randomizationFactor)
        }

        // and: the exception message should be correct
        assertEquals("Randomization factor must be between 0 and 1", exception.message)

        // given: another invalid randomization factor
        val randomizationFactor2 = -0.1

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception2 = assertFailsWith<IllegalArgumentException> {
            constant(delay, randomizationFactor2)
        }

        // and: the exception message should be correct
        assertEquals("Randomization factor must be between 0 and 1", exception2.message)
    }

    @Test
    fun linearDelayStrategyWithInvalidInitialDelay() = runTest {
        // given: linear delay strategy parameters
        val initialDelay = ZERO

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            linear(initialDelay)
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
            linear(initialDelay, maxDelay = maxDelay)
        }

        // and: the exception message should be correct
        assertEquals("Max delay duration must be greater than initial delay", exception.message)
    }

    @Test
    fun linearDelayStrategyWithInvalidMultiplier() = runTest {
        // given: linear delay strategy parameters
        val initialDelay = 500.milliseconds
        val multiplier = 0.0

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            linear(initialDelay, multiplier = multiplier)
        }

        // and: the exception message should be correct
        assertEquals("Multiplier must be greater than 0", exception.message)
    }

    @Test
    fun linearDelayStrategyWithInvalidRandomizationFactor() = runTest {
        // given: linear delay strategy parameters
        val initialDelay = 500.milliseconds
        val randomizationFactor = 1.1

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            linear(initialDelay, randomizationFactor = randomizationFactor)
        }

        // and: the exception message should be correct
        assertEquals("Randomization factor must be between 0 and 1", exception.message)

        // given: another invalid randomization factor
        val randomizationFactor2 = -0.1

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception2 = assertFailsWith<IllegalArgumentException> {
            linear(initialDelay, randomizationFactor = randomizationFactor2)
        }

        // and: the exception message should be correct
        assertEquals("Randomization factor must be between 0 and 1", exception2.message)
    }

    @Test
    fun exponentialDelayStrategyWithInvalidInitialDelay() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = ZERO

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            exponential(initialDelay)
        }

        // and: the exception message should be correct
        assertEquals("Initial delay duration must be greater than zero", exception.message)
    }

    @Test
    fun exponentialDelayStrategyWithInvalidMaxDelay() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = 500.milliseconds
        val maxDelay = (-1).minutes

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            exponential(initialDelay, maxDelay)
        }

        // and: the exception message should be correct
        assertEquals("Max delay duration must be greater than initial delay", exception.message)
    }

    @Test
    fun exponentialDelayStrategyWithInvalidMultiplier() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = 500.milliseconds
        val multiplier = 0.0

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            exponential(initialDelay, multiplier = multiplier)
        }

        // and: the exception message should be correct
        assertEquals("Multiplier must be greater than 1", exception.message)
    }

    @Test
    fun exponentialDelayStrategyWithInvalidRandomizationFactor() = runTest {
        // given: exponential delay strategy parameters
        val initialDelay = 500.milliseconds
        val randomizationFactor = 1.1

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception = assertFailsWith<IllegalArgumentException> {
            exponential(initialDelay, randomizationFactor = randomizationFactor)
        }

        // and: the exception message should be correct
        assertEquals("Randomization factor must be between 0 and 1", exception.message)

        // given: another invalid randomization factor
        val randomizationFactor2 = -0.1

        // when: the delay duration is calculated
        // then: an exception should be thrown
        val exception2 = assertFailsWith<IllegalArgumentException> {
            exponential(initialDelay, randomizationFactor = randomizationFactor2)
        }

        // and: the exception message should be correct
        assertEquals("Randomization factor must be between 0 and 1", exception2.message)
    }

}
