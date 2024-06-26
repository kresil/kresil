package kresil.core.semaphore

import kotlin.time.Duration

/**
 * Provides a mechanism for controlling access to shared resources using a set of permits.
 * Behaves similarly to a [Semaphore](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Semaphore.html),
 * but if no permits are available, the caller suspends until that request can be granted.
 * Supports both counting and binary semaphores.
 */
internal interface SuspendableSemaphore {

    /**
     * Acquires n [permits] from the semaphore or suspends for the specified [timeout] if the permits
     * are not available.
     * @param permits the number of permits to acquire.
     * @param timeout the maximum time to wait for the permits.
     * @throws IllegalStateException if the specified timeout is exceeded.
     */
    @Throws(IllegalStateException::class)
    suspend fun acquire(permits: Int, timeout: Duration)

    /**
     * Releases n [permits] back to the semaphore.
     * @param permits the number of permits to release.
     */
    suspend fun release(permits: Int)

    // TODO: add drain operation
}
