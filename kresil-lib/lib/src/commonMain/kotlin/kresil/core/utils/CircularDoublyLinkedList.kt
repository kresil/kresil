package kresil.core.utils

import kresil.core.queue.Node
import kresil.core.queue.Queue

/**
 * Provides a basic implementation of an intrusive circular doubly linked non-thread safe list.
 * Allows for efficient insertion or removal of elements from any position in the list,
 * as the nodes are not stored contiguously in memory (unlike an array).
 */
internal class CircularDoublyLinkedList<T> : Queue<T> {

    private class NodeImpl<T>(val maybeValue: T?) : Node<T> {

        constructor (maybeValue: T?, n: NodeImpl<T>, p: NodeImpl<T>) : this(maybeValue) {
            next = n
            prev = p
        }

        lateinit var next: NodeImpl<T>
        lateinit var prev: NodeImpl<T>

        override val value: T
            get() {
                require(maybeValue != null) { "Only nodes with non-null values can be exposed publicly" }
                return maybeValue
            }
    }

    private val head: NodeImpl<T> = NodeImpl(null)

    init {
        head.next = head
        head.prev = head
    }

    /**
     * The number of elements in the list.
     */
    override var size = 0
        private set

    /**
     * Adds a new element to the end of the list.
     * @param value the value to add.
     * @return the node that was added.
     */
    override fun enqueue(value: T): Node<T> {
        val tail: NodeImpl<T> = head.prev
        val node: NodeImpl<T> = NodeImpl(value, head, tail)
        head.prev = node
        tail.next = node
        size += 1
        return node
    }

    /**
     * Checks if the list is empty.
     */
    val empty: Boolean
        get() = head === head.prev

    /**
     * Checks if the list is not empty.
     */
    val notEmpty: Boolean
        get() = !empty

    /**
     * If the list is not empty, returns the value of the head node otherwise returns null.
     */
    val headValue: T?
        get() {
            return if (notEmpty) {
                head.next.value
            } else {
                null
            }
        }

    /**
     * If the list is not empty, returns the head node otherwise returns null.
     */
    val headNode: Node<T>?
        get() {
            return if (notEmpty) {
                head.next
            } else {
                null
            }
        }

    /**
     * Checks if the given node is the head node.
     * @param node the node to check.
     * @return true if the node is the head node, false otherwise.
     */
    fun isHeadNode(node: Node<T>) = head.next === node

    /**
     * Checks if the head node satisfies the given condition.
     * @param cond the condition to be applied to the head node value.
     * @return true if the head node satisfies the condition, false otherwise.
     */
    override inline fun headCondition(cond: (T) -> Boolean): Boolean = headValue?.let { cond(it) } == true

    /**
     * Removes the head node from the list and returns it if the list is not empty.
     * @throws IllegalStateException if the list is empty.
     */
    override fun dequeue(): Node<T> {
        require(!empty) { "cannot pull from an empty list" }
        val node = head.next
        head.next = node.next
        node.next.prev = head
        size -= 1
        return node
    }

    /**
     * Removes the given node from the list.
     * @param node the node to remove.
     * @throws IllegalArgumentException if the node is not part of the list.
     */
    override fun remove(node: Node<T>) {
        require(!empty) { "cannot remove from an empty list" }
        require(node is NodeImpl<T>) { "node must be an internal node" }
        node.prev.next = node.next
        node.next.prev = node.prev
        size -= 1
    }
}
