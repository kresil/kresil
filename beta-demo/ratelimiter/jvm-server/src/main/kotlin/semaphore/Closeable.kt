package semaphore

/**
 * Represents a resource that should be closed.
 */
fun interface Closeable {
    /**
     * Closes the resource.
     */
    fun close()
}
