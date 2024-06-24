package kresil.ratelimiter.semaphore

import kresil.ratelimiter.exceptions.RateLimiterRejectedException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

interface KeyBasedSemaphore<T> {

    /**
     * Acquires n permits from the semaphore associated with the given key.
     * @param key the key to acquire the permits for.
     * @param permits the number of permits to acquire.
     * @param timeout the maximum time to wait for the permits. If null, the call will wait indefinitely or until a third party timeout occurs.
     */
    @Throws(RateLimiterRejectedException::class, CancellationException::class)
    suspend fun acquire(key: T, permits: Int = 1, timeout: Duration? = null)

    /**
     * Releases n permits from the semaphore associated with the given key.
     * @param key the key to release the permit for.
     * @param permits the number of permits to release.
     */
    suspend fun release(key: T, permits: Int = 1)
}
