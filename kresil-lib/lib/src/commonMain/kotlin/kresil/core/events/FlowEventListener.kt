package kresil.core.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Represents a listener mechanism that can be used to listen to events of type [Event].
 * Uses a shared flow to emit events, which can be listened to by registering listeners.
 * The asynchronous nature of the listeners is controlled by the [scope] in which they are launched.
 * Registered listeners can be cancelled at any time.
 * @param Event the type of the event to be listened to.
 */
interface FlowEventListener<Event> {

    /**
     * A mutable shared flow that emits events of type [Event].
     * Such events can be listened to by:
     * - registering an unspecific listener, see [onEvent];
     * - registering a listener for a specific event, which is a subtype of [Event] and depends on the implementation.
     */
    val events: MutableSharedFlow<Event>

    /**
     * The scope in which each listener will be launched upon registration.
     */
    val scope: CoroutineScope

    /**
     * Registers a listener that will be called when an event of type [Event] is emitted.
     * @param action the action to be executed when an unspecific event is emitted.
     */
    suspend fun onEvent(action: suspend (Event) -> Unit)

    /**
     * Cancels all listeners registered.
     * Subsequent registrations should not be affected.
     */
    suspend fun cancelListeners()
}
