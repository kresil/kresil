package kresil.retry.delay

import kotlin.time.Duration

/**
 * Specifies the delay strategy to use for retrying an operation.
 * Presents the same behaviour as a [RetryDelayStrategy],
 * with the added ability to use a custom delay provider.
 * The strategy is used to determine the delay duration between retries, where:
 * - `attempt` is the current retry attempt. Starts at **1**.
 * - `lastThrowable` is the last throwable caught, if any.
 *
 * If the return value is `Duration.ZERO`,
 * the delay is considered to be **defined externally** or not needed
 * (**no delay**), as such, the **default delay provider is skipped**.
 */
typealias RetryDelayStrategy = suspend (attempt: Int, lastThrowable: Throwable?) -> Duration
