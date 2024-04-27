import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kresil.retry.Retry
import kresil.retry.builders.retryConfig
import kresil.retry.config.RetryConfig
import kotlin.time.Duration.Companion.milliseconds

class WebServiceException : Exception()

fun interface RemoteService {
    suspend fun suspendCall()
}

fun main() {
    val remoteService = RemoteService {
        throw WebServiceException()
    }

    val config: RetryConfig = retryConfig {
        maxAttempts = 3 // includes the first non-retry currentAttempt
        retryIf { it is WebServiceException }
        constantDelay(500.milliseconds)
    }

    val retry = Retry(config) // or Retry() for default config
    runBlocking {
        launch {
            // for all events
            retry.onEvent { println(it) }
            retry.onRetry { currAttempt ->
                // handle retry event
            }
            retry.onError { error ->
                // handle error event
            }
            // and more...
        }

        retry.executeSuspendFunction {
            remoteService.suspendCall()
        }
        // or:
        val decorated = retry.decorateSuspendFunction {
            remoteService.suspendCall()
        }
        // and call it later: decorated()
    }

}
