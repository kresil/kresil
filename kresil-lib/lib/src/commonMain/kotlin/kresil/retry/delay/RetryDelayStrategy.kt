package kresil.retry.delay

import kotlin.time.Duration

/**
 * Specifies the delay strategy to use for retrying an operation.
 * The strategy is used to determine the delay duration between retries, where:
 * - `attempt` is the current retry attempt. Starts at **1**.
 * - `lastThrowable` is the last throwable caught, if any.
 * @return the [Duration] to delay before the next retry.
 */
typealias RetryDelayStrategy = (attempt: Int, lastThrowable: Throwable?) -> Duration
