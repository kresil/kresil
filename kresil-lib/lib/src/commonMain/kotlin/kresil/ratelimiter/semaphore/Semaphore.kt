package kresil.ratelimiter.semaphore

import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

interface Semaphore {

    /**
     * Acquires n [permits] from the semaphore or suspends for the specified [timeout] if the permits
     * are not available.
     * @param permits the number of permits to acquire.
     * @param timeout the maximum time to wait for the permits.
     */
    @Throws(RateLimiterRejectedException::class, CancellationException::class)
    suspend fun acquire(permits: Int, timeout: Duration)

    /**
     * Releases n [permits] back to the semaphore.
     * @param permits the number of permits to release.
     */
    suspend fun release(permits: Int)
}
