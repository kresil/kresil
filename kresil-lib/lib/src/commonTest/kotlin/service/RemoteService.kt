package service

/**
 * Represents a remote service that can be called remotely.
 */
fun interface RemoteService {
    suspend fun suspendCall(): String
}
