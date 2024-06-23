package kresil.core

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MutexExampleTests {

    @Test
    fun suspendingWhileHoldingAMutexBehaviour() = runTest {
        val mutex = Mutex()

        suspend fun innerFunction() {
            println("suspending while holding a mutex")
            delay(3000) // while suspended, this coroutine is holding the mutex
            println("after delay")
        }
        launch {
            mutex.withLock {
                println("'main' coroutine acquired the mutex")
                innerFunction()
                println("'main' coroutine released the mutex")
            }
        }
        delay(1000)
        launch {
            mutex.withLock {
                println("another coroutine acquired the mutex")
            }
        }
    }

    @Test
    fun mutexIsNotReentrant() = runTest {
        val mutex = Mutex()
        suspend fun innerFunction() {
            println("a coroutine tries to acquire the mutex while holding it")
            // without timeout, this coroutine would be suspended indefinitely
            withTimeout(3000) {
                mutex.withLock {
                    println("supposedly unreachable code")
                }
            }
        }

        launch {
            mutex.withLock {
                println("'main' coroutine acquired the mutex")
                assertFailsWith<TimeoutCancellationException> {
                    innerFunction()
                }
                println("'main' coroutine released the mutex")
            }
        }
    }
}
