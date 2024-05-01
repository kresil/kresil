package service

/**
 * Represents a service that can be called remotely.
 */
interface RemoteService {
    suspend fun suspendSupplier(): String?
    suspend fun suspendFunction(input: String): String?
    suspend fun suspendBiFunction(a: String, b: String): String?
}
