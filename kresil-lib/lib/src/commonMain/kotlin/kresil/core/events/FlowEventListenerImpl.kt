package kresil.core.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Represents the default implementation of a listener mechanism that can be used to listen to events of type [Event].
 * Uses a [MutableSharedFlow] to emit events, which can be listened to by registering listeners.
 * Listener [onEvent] can be used to register an unspecific listener that will be called when an event is emitted.
 * Implementations should provide additional methods to register listeners for specific events.
 * This implementation uses a [CoroutineScope] with a [Job] and [Dispatchers.Default] to control
 * the asynchronous nature of the listener's execution.
 * Registered listeners can be cancelled at any time without affecting subsequent registrations, see [cancelListeners].
 * @param Event the type of the event to be listened to.
 */
open class FlowEventListenerImpl<Event> internal constructor() : FlowEventListener<Event> {

    override val events = MutableSharedFlow<Event>()

    override val scope: CoroutineScope = CoroutineScope(
        // TODO: Is supervisor job needed? Connect this job with an outer parent by providing a constructor parameter?
        Job() + Dispatchers.Default
    )

    override suspend fun onEvent(action: suspend (Event) -> Unit): Job =
        scope.launch {
            events.collect { action(it) }
        }

    override fun cancelListeners() {
        // does not cancel the underlying job (it would with scope.cancel())
        scope.coroutineContext.cancelChildren()
    }

    /**
     * Registers a listener that will be called when a specific subtype of [Event] is emitted.
     * @param action the action to be executed when a specific event is emitted.
     * @return a [Job] representing the listener, which can be used to cancel it.
     */
    suspend inline fun <reified EventType : Event> onSpecificEvent(
        crossinline action: suspend (EventType) -> Unit,
    ) = scope.launch {
        events
            .filterIsInstance<EventType>()
            .collect { action(it) }
    }
}
