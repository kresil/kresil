package kresil.circuitbreaker.slidingwindow

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class CountBasedSlidingWindow(override val capacity: Int) : SlidingWindow<Boolean> {
    private val arrayDeque: ArrayDeque<Boolean> by dequeLimiter(capacity)

    override fun recordSuccess() {
        arrayDeque.add(true)
    }

    override fun recordFailure() {
        arrayDeque.add(false)
    }

    override fun currentFailureRate(): Double {
        return arrayDeque.count { !it }.toDouble() / capacity
    }

    override fun clear() {
        arrayDeque.clear()
    }
}

private fun <E> dequeLimiter(limit: Int): ReadWriteProperty<Any?, ArrayDeque<E>> =
    object : ReadWriteProperty<Any?, ArrayDeque<E>> {

        private var deque: ArrayDeque<E> = ArrayDeque(limit)

        private fun applyLimit() {
            while (deque.size > limit) {
                val removed = deque.removeFirst()
                println("dequeLimiter removed $removed")
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): ArrayDeque<E> {
            applyLimit()
            return deque
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: ArrayDeque<E>) {
            this.deque = value
            applyLimit()
        }
    }
