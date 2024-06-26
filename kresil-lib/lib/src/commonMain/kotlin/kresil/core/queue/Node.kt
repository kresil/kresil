package kresil.core.queue

/**
 * Represents a node in a [Queue] data structure.
 * @param T the type of value held in this node.
 */
interface Node<T> {
    val value: T
}
