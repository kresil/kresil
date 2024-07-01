package kresil.core.utils

import kresil.core.queue.Node
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CircularDoublyLinkedListTest {

    @Test
    fun enqueueShouldIncreaseSize() = runTest {
        // given: an empty list
        val list = CircularDoublyLinkedList<Int>()
        assertEquals(0, list.size)

        // when: a value is enqueued
        list.enqueue(1)

        // then: the size of the list should increase
        assertEquals(1, list.size)
    }

    @Test
    fun dequeueShouldDecreaseSize() = runTest {
        // given: a list with two elements
        val list = CircularDoublyLinkedList<Int>()
        list.enqueue(1)
        list.enqueue(2)
        assertEquals(2, list.size)

        // when: an element is dequeued
        list.dequeue()

        // then: the size of the list should decrease
        assertEquals(1, list.size)
    }

    @Test
    fun dequeueShouldRespectFifoOrder() = runTest {
        // given: a list with two elements
        val list = CircularDoublyLinkedList<Int>()
        list.enqueue(1)
        list.enqueue(2)

        // when: an element is dequeued
        val node: Node<Int> = list.dequeue()

        // then: the dequeued element should be the first one added
        assertEquals(1, node.value)
    }

    @Test
    fun isEmptyShouldReturnTrueWhenListIsEmpty() {
        // given: an empty list
        val list = CircularDoublyLinkedList<Int>()

        // then: the list should be empty
        assertTrue(list.empty)
        assertFalse(list.notEmpty)
    }

    @Test
    fun isEmptyShouldReturnFalseWhenListIsNotEmpty() = runTest {
        // given: an empty list
        val list = CircularDoublyLinkedList<Int>()

        // when: a value is enqueued
        list.enqueue(1)

        // then: the list should not be empty
        assertFalse(list.empty)
        assertTrue(list.notEmpty)
    }

    @Test
    fun headValueShouldReturnNullWhenListIsEmpty() {
        // given: an empty list
        val list = CircularDoublyLinkedList<Int>()

        // then: headValue should be null
        assertNull(list.headValue)
    }

    @Test
    fun headValueShouldReturnFirstElementWhenListIsNotEmpty() = runTest {
        // given: a list with two elements
        val list = CircularDoublyLinkedList<Int>()
        list.enqueue(1)
        list.enqueue(2)

        // then: headValue should return the first element
        assertEquals(1, list.headValue)
    }

    @Test
    fun removeShouldDecreaseSize() = runTest {
        // given: a list with two elements
        val list = CircularDoublyLinkedList<Int>()
        val node1 = list.enqueue(1)
        val node2 = list.enqueue(2)

        // when: the first element is removed
        list.remove(node1)

        // then: the size of the list should decrease
        assertEquals(1, list.size)

        // when: the second element is removed
        list.remove(node2)

        // then: the size of the list should be zero
        assertEquals(0, list.size)
    }

    @Test
    fun removeShouldRemoveCorrectElement() = runTest {
        // given: a list with two elements
        val list = CircularDoublyLinkedList<Int>()
        val node1 = list.enqueue(1)
        list.enqueue(2)

        // when: the first element is removed
        list.remove(node1)

        // then: the head value should be the second element
        assertEquals(2, list.headValue)
    }

    @Test
    fun headConditionShouldReturnTrueIfConditionMatches() = runTest {
        // given: a list with one element
        val list = CircularDoublyLinkedList<Int>()
        list.enqueue(1)

        // when: checking the head condition with a matching predicate
        val result = list.headCondition { it == 1 }

        // then: the condition should return true
        assertTrue(result)
    }

    @Test
    fun headConditionShouldReturnFalseIfConditionDoesNotMatch() = runTest {
        // given: a list with one element
        val list = CircularDoublyLinkedList<Int>()
        list.enqueue(1)

        // when: checking the head condition with a non-matching predicate
        val result = list.headCondition { it == 2 }

        // then: the condition should return false
        assertFalse(result)
    }

    @Test
    fun dequeueShouldThrowExceptionWhenListIsEmpty() = runTest {
        // given: an empty list
        val list = CircularDoublyLinkedList<Int>()

        // then: an exception should be thrown when dequeue is called
        val exception = assertFailsWith<IllegalArgumentException> {
            list.dequeue()
        }
        assertEquals("cannot pull from an empty list", exception.message)
    }

    @Test
    fun removeShouldThrowExceptionWhenListIsEmpty() = runTest {
        // given: a list with one element
        val list = CircularDoublyLinkedList<Int>()
        val node = list.enqueue(1)

        // when: the element is removed
        list.remove(node)

        // and: the list is empty
        // then: an exception should be thrown when remove is called again
        val exception = assertFailsWith<IllegalArgumentException> {
            list.remove(node)
        }
        assertEquals("cannot remove from an empty list", exception.message)

    }

    @Test
    fun removeShouldThrowExceptionForInvalidNode() = runTest {
        // given: a list with one element
        val list = CircularDoublyLinkedList<Int>()
        list.enqueue(1)

        // and: an invalid node
        val invalidNode = object : Node<Int> {
            override val value: Int
                get() = 999
        }

        // then: an exception should be thrown when an invalid node is removed
        val exception = assertFailsWith<IllegalArgumentException> {
            list.remove(invalidNode)
        }
        assertEquals("node must be an internal node", exception.message)
    }
}
