fun interface RemoteService {
    suspend fun suspendCall(): String
}
