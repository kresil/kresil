package kresil.core.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Represents a listener mechanism that can be used to listen to events of type [Event].
 * Provides
 */
open class FlowEventListenerImpl<Event> internal constructor() : FlowEventListener<Event> {

    override val events = MutableSharedFlow<Event>()

    override val scope: CoroutineScope = CoroutineScope(
        // TODO: Is supervisor job needed? Connect this job with an outer parent by providing a constructor parameter?
        Job() + Dispatchers.Default
    )

    override suspend fun onEvent(action: suspend (Event) -> Unit) {
        scope.launch {
            events.collect { action(it) }
        }
    }

    override fun cancelListeners() {
        // does not cancel the underlying job (it would with scope.cancel())
        scope.coroutineContext.cancelChildren()
    }
}
