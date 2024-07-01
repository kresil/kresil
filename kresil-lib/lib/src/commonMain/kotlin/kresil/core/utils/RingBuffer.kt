package kresil.core.utils

internal class RingBuffer<T>(val capacity: Int) : Iterable<T> {

    @Suppress("UNCHECKED_CAST")
    private val buffer: Array<T?> = arrayOfNulls<Any>(capacity) as Array<T?>

    // positional index (pix) - the index of the next element to be added
    private var pix: Int = 0
    private var hasReachedFullCapacity: Boolean = false

    fun add(element: T) {
        buffer[pix] = element
        val newPix = (pix + 1) % capacity
        // has come full circle (reached the end of the buffer)
        if (newPix == 0) {
            hasReachedFullCapacity = true
        }
        pix = newPix
    }

    val size: Int
        get() {
            return if (hasReachedFullCapacity) capacity else pix
        }

    fun get(index: Int): T? {
        if (index < 0 || index >= capacity) {
            throw IndexOutOfBoundsException("Index $index out of bounds for ring buffer of capacity $capacity")
        }
        return buffer[index]
    }

    fun toList(): List<T?> {
        return iterator().asSequence().toList()
    }

    val isFull: Boolean
        get() {
            return size == capacity
        }

    val isEmpty: Boolean
        get() {
            return size == 0
        }

    fun clear() {
        for (i in 0 until capacity) {
            buffer[i] = null
        }
        hasReachedFullCapacity = false
        pix = 0
    }

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var index = 0
        private val firstNullableIndex = buffer.indexOfFirst { it == null }

        override fun hasNext(): Boolean {
            // on first null element, the buffer is considered empty
            // as the only way to have a null element is to have not filled the buffer yet
            // or to have cleared it
            val lastIndex = if (firstNullableIndex != -1) firstNullableIndex else capacity
            return index < lastIndex
        }

        override fun next(): T {
            if (!hasNext()) {
                throw NoSuchElementException()
            }
            // Return the next non-null element
            return buffer[index++]!!
        }
    }
}
