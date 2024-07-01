import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kresil.core.queue.Queue
import kresil.core.queue.Node
import semaphore.Closeable
import java.util.*

class RedisSemaphoreQueue<T> : Queue<T>, Closeable {
    private val redisClient: RedisClient = RedisClient.create("redis://localhost:6379")
    private val connection: StatefulRedisConnection<String, String> = redisClient.connect()
    private val commands: RedisCommands<String, String> = connection.sync()
    private val json = Json
    private val queueKey = "queue"

    @Serializable
    private data class NodeImpl<T>(
        override val value: T,
        val id: String = UUID.randomUUID().toString()
    ) : Node<T>

    override val size: Int
        get() = commands.llen(queueKey).toInt()

    override suspend fun enqueue(value: T): Node<T> {
        val node = NodeImpl(value)
        val jsonString = json.encodeToString(node)
        commands.rpush(queueKey, jsonString)
        return node
    }

    override suspend fun dequeue(): Node<T> {
        val jsonString = commands.lpop(queueKey) ?: throw IllegalStateException("Queue is empty")
        return json.decodeFromString(jsonString)
    }

    override suspend fun remove(node: Node<T>) {
        val jsonString = json.encodeToString(node)
        commands.lrem(queueKey, 1, jsonString)
    }

    override suspend fun headCondition(cond: (T) -> Boolean): Boolean {
        val jsonString = commands.lindex(queueKey, 0) ?: return false
        val headNode: Node<T> = json.decodeFromString(jsonString)
        return cond(headNode.value)
    }

    override fun close() {
        connection.close()
        redisClient.shutdown()
    }
}
