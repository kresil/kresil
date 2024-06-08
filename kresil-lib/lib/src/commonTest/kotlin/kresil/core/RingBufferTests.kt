package kresil.core

import kresil.core.utils.RingBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingBufferTests {

    @Test
    fun fullCapacityBufferEvictsFirstElementUponAddition() {
        // given: a ring buffer with a defined capacity
        val capacity = 3
        val buffer = RingBuffer<Int>(capacity)

        // when: elements are added to the buffer to exceed its capacity
        repeat(4) { buffer.add(it + 1) }

        // then: the first element added should have been evicted
        assertEquals(listOf(4, 2, 3), buffer.toList())
    }

    @Test
    fun fullCapacityBufferShouldWorkAsASlidingWindow() {
        // given: a ring buffer with a defined capacity
        val capacity = 3
        val buffer = RingBuffer<Int>(capacity)

        // when: elements are added to the buffer to fill its capacity
        repeat(capacity * 2) { buffer.add(it + 1) }

        // then: the buffer should only keep the last capacity elements
        assertEquals(listOf(4, 5, 6), buffer.toList())
    }

    @Test
    fun indexingOutOfBoundsShouldThrowException() {
        // given: a ring buffer with a defined capacity
        val capacity = 3
        val buffer = RingBuffer<Int>(capacity)

        // when: an index out of bounds is requested
        // then: a IndexOutOfBoundsException should be thrown
        val exA = assertFailsWith<IndexOutOfBoundsException> { buffer.get(-1) }
        assertEquals("Index -1 out of bounds for ring buffer of capacity 3", exA.message)

        // when: an index out of bounds is requested
        // then: a IndexOutOfBoundsException should be thrown
        val exB = assertFailsWith<IndexOutOfBoundsException> { buffer.get(capacity) }
        assertEquals("Index 3 out of bounds for ring buffer of capacity 3", exB.message)
    }

    @Test
    fun iteratorShouldVisitAllElements() {

        // given: a ring buffer with a defined capacity
        val capacity = 3
        val buffer = RingBuffer<Int>(capacity)

        // when: elements are added to the buffer that fills it
        repeat(capacity) { buffer.add(it + 1) }

        // then: the iterator should visit all elements
        val iteratedElements = mutableListOf<Int>()
        for (element in buffer) {
            iteratedElements.add(element)
        }
        assertEquals(listOf(1, 2, 3), iteratedElements)
    }

    @Test
    fun clearShouldRemoveAllElementsAndAllowReusingBuffer() {
        // given: a ring buffer with a defined capacity
        val capacity = 3
        val buffer = RingBuffer<Int>(capacity)

        // when: elements are added to the buffer
        repeat(capacity) { buffer.add(it + 1) }

        // then: the buffer should contain elements
        assertEquals(listOf(1, 2, 3), buffer.toList())

        // when: the buffer is cleared
        buffer.clear()

        // then: the buffer should be empty
        assertEquals(emptyList(), buffer.toList())

        // when: elements are added to the buffer
        repeat(capacity) { buffer.add(it + 1) }

        // then: the buffer should contain elements in the correct positions
        assertEquals(listOf(1, 2, 3), buffer.toList())
    }

    @Test
    fun emptyBufferShouldBeConsideredEmpty() {
        // given: a ring buffer with a defined capacity
        val capacity = 3
        val buffer = RingBuffer<Int>(capacity)

        // then: the buffer should be empty
        assertTrue(buffer.isEmpty)

        // when: elements are added to the buffer
        buffer.add(1)

        // then: the buffer should not be empty
        assertFalse(buffer.isEmpty)
    }

    @Test
    fun fullBufferShouldBeConsideredFull() {
        // given: a ring buffer with a defined capacity
        val capacity = 3
        val buffer = RingBuffer<Int>(capacity)

        // then: the buffer should not be full
        assertFalse(buffer.isFull)

        // when: elements are added to the buffer
        repeat(capacity) { buffer.add(it + 1) }

        // then: the buffer should be full
        assertTrue(buffer.isFull)

        // when: more elements are added to the buffer
        buffer.add(4)

        // then: the buffer should still be full
        assertTrue(buffer.isFull)
    }

    @Test
    fun sizeShouldReturnNumberOfElementsInBuffer() {
        // given: a ring buffer with a defined capacity
        val capacity = 3
        val buffer = RingBuffer<Int>(capacity)

        // then: the buffer should be empty
        assertEquals(0, buffer.size)

        // when: elements are added to the buffer
        buffer.add(1)

        // then: the buffer should contain one element
        assertEquals(1, buffer.size)

        // when: more elements are added to the buffer to fill it
        repeat(capacity - 1) { buffer.add(it + 1) }

        // then: the buffer should be at full capacity
        assertEquals(capacity, buffer.size)

        // when: more elements are added to the buffer
        buffer.add(4)

        // then: the buffer should still contain three elements
        assertEquals(capacity, buffer.size)
    }

}
