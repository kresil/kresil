package kresil.core.utils

/**
 * Provides a basic implementation of an intrusive doubly linked non-thread safe list with essential operations
 * for adding, accessing, and removing elements.
 * Most notably the [remove] operation is **O(1)**.
 */
internal class NodeLinkedList<T> {

    interface Node<T> {
        val value: T
    }

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
    var size = 0
        private set

    /**
     * Adds a new element to the end of the list.
     * @param value the value to add.
     * @return the node that was added.
     */
    fun enqueue(value: T): Node<T> {
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
    inline fun headCondition(cond: (T) -> Boolean): Boolean = headValue?.let { cond(it) } == true

    /**
     * Removes the head node from the list and returns it.
     * @return the removed node.
     * @throws IllegalStateException if the list is empty.
     */
    fun pull(): Node<T> {
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
    fun remove(node: Node<T>) {
        require(node is NodeImpl<T>) { "node must be an internal node" }
        node.prev.next = node.next
        node.next.prev = node.prev
        size -= 1
    }
}
