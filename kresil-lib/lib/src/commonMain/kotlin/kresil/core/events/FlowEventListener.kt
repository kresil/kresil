package kresil.core.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Represents a listener mechanism that can be used to listen to events of type [Event].
 * Uses a [MutableSharedFlow] to emit events, which can be listened to by registering listeners.
 * Listener [onEvent] can be used to register an unspecific listener that will be called when an event is emitted.
 * Implementations should provide additional methods to register listeners for specific events.
 * The asynchronous nature of the listener's execution is controlled by the [scope] in which they are launched.
 * Registered listeners can be cancelled at any time without affecting subsequent registrations.
 * @param Event the type of the event to be listened to.
 */
internal interface FlowEventListener<Event> {

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
     * @return a [Job] representing the listener, which can be used to cancel it.
     */
    suspend fun onEvent(action: suspend (Event) -> Unit): Job

    /**
     * Cancels all listeners registered.
     * Subsequent registrations should not be affected.
     */
    fun cancelListeners()

}
