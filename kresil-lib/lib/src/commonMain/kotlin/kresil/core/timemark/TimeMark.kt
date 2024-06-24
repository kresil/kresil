package kresil.core.timemark

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Returns the current time mark.
 */
internal fun getCurrentTimeMark(): ComparableTimeMark = TimeSource.Monotonic.markNow()

/**
 * Checks if the time mark elapsed duration has exceeded the given duration.
 * @param timeMark the time mark to check.
 * @param duration the duration to check against.
 * @return true if the time mark has exceeded the duration, false otherwise.
 */
internal fun hasExceededDuration(timeMark: ComparableTimeMark, duration: Duration): Boolean =
    timeMark.elapsedNow() >= duration
