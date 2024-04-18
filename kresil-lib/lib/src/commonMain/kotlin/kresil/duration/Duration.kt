package kresil.duration

import kotlinx.datetime.DateTimeUnit

data class Duration(
    val nanoseconds: Long,
)

val Int.nanoseconds: Duration
    get() = Duration(DateTimeUnit.TimeBased(DateTimeUnit.NANOSECOND.nanoseconds * this.toLong()).nanoseconds)

val Int.microseconds: Duration
    get() = Duration(DateTimeUnit.TimeBased(DateTimeUnit.MICROSECOND.nanoseconds * this.toLong()).nanoseconds)

val Int.milliseconds: Duration
    get() = Duration(DateTimeUnit.TimeBased(DateTimeUnit.MILLISECOND.nanoseconds * this.toLong()).nanoseconds)

val Int.seconds: Duration
    get() = Duration(DateTimeUnit.TimeBased(DateTimeUnit.SECOND.nanoseconds * this.toLong()).nanoseconds)

val Int.minutes: Duration
    get() = Duration(DateTimeUnit.TimeBased(DateTimeUnit.MINUTE.nanoseconds * this.toLong()).nanoseconds)

val Int.hours: Duration
    get() = Duration(DateTimeUnit.TimeBased(DateTimeUnit.HOUR.nanoseconds * this.toLong()).nanoseconds)

// TODO: should we support days, weeks, months, and years?
val Int.days: DateTimeUnit.DayBased
    get() = DateTimeUnit.DayBased(days = this)

val Int.weeks: DateTimeUnit.DayBased
    get() = DateTimeUnit.DayBased(days = this * 7)

val Int.months: DateTimeUnit.MonthBased
    get() = DateTimeUnit.MonthBased(months = this)

val Int.years: DateTimeUnit.MonthBased
    get() = DateTimeUnit.MonthBased(months = this * 12)
