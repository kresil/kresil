package kresil.core.queue

/**
 * Represents a queue data structure that follows the FIFO (First-In-First-Out) principle.
 */
interface Queue<T> {
    /**
     * Returns the number of elements in the queue.
     */
    val size: Int

    /**
     * Enqueues a new element into the queue.
     * @param value the value to enqueue.
     * @return the node that was added to the queue.
     */
    suspend fun enqueue(value: T): Node<T>

    /**
     * Dequeues the head element from the queue.
     * @return the node that was removed from the queue.
     * @throws IllegalStateException if the queue is empty.
     */
    suspend fun dequeue(): Node<T>

    /**
     * Removes a specific node from the queue.
     * @param node the node to remove from the queue.
     * @throws IllegalArgumentException if the node is not in the queue.
     */
    suspend fun remove(node: Node<T>)

    /**
     * Checks if the given condition on the head element of the queue is satisfied.
     * @param cond the condition to be applied to the head element.
     * @return true if the condition is satisfied by the head element, false otherwise.
     */
    suspend fun headCondition(cond: (T) -> Boolean): Boolean
}
