package kresil.core.delay

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * Validates that the duration is in fact a non-negative duration.
 * @throws IllegalArgumentException if the duration is less than 0
 */
@Throws(IllegalArgumentException::class)
fun Duration.requireNonNegative(qualifier: String) {
    require(this >= ZERO) { "$qualifier duration must be greater than or equal to zero" }
}

/**
 * Validates that the duration is in fact a positive duration.
 * @throws IllegalArgumentException if the duration is less than or equal to 0
 */
@Throws(IllegalArgumentException::class)
fun Duration.requirePositive(qualifier: String) {
    require(this > ZERO) { "$qualifier duration must be greater than zero" }
}
