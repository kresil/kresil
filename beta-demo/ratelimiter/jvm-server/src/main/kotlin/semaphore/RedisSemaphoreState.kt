package semaphore

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kresil.ratelimiter.semaphore.state.SemaphoreState
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.parseIsoString
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class RedisSemaphoreState : SemaphoreState, Closeable {
    private val redisClient: RedisClient = RedisClient.create("redis://localhost:6379")
    private val connection: StatefulRedisConnection<String, String> = redisClient.connect()
    private val commands: RedisCommands<String, String> = connection.sync()

    private companion object {
        private const val PERMITS_KEY = "permits"
        private const val ELAPSED_DURATION_KEY = "elapsedDuration"
    }

    override var permitsInUse: Int
        get() = commands.get(PERMITS_KEY)?.toInt() ?: 0
        set(value) {
            commands.set(PERMITS_KEY, value.toString())
        }

    // since ComparableTimeMark is not serializable, we store the elapsed duration instead
    private var elapsedDuration: Duration
        get() = commands.get(ELAPSED_DURATION_KEY)?.let { parseIsoString(it) } ?: 0.seconds
        set(value) {
            commands.set(ELAPSED_DURATION_KEY, value.toIsoString())
        }

    override var refreshTimeMark: ComparableTimeMark
        get() = TimeSource.Monotonic.markNow() - elapsedDuration
        set(value) {
            elapsedDuration = value.elapsedNow()
        }

    override fun setPermits(updateFunction: (Int) -> Int) {
        permitsInUse = updateFunction(permitsInUse)
    }

    override fun setRefreshTimeMark(value: ComparableTimeMark) {
        refreshTimeMark = value
    }

    override fun close() {
        connection.close()
        redisClient.shutdown()
    }

}
