package kresil.core.reducer

/**
 * Similiar to React's [useReducer](https://react.dev/reference/react/useReducer)
 * hook, this contract defines a reducer that can be used to manage the state of a component.
 * A reducer should be used to manage the state of a component in a deterministic way (i.e., given the same
 * sequence of events, the reducer should always produce the same state - pure function).
 *
 * To alter the internal state of a component, callers can use [dispatch] to emit events.
 * The current state can be consulted using the [currentState] method.
 * @param State the type of the state.
 * @param Event the type of the event.
 */
internal interface Reducer<State, Event> {

    /**
     * Dispatches an event to the reducer, which can trigger a state transition.
     * @param event the event to dispatch.
     */
    suspend fun dispatch(event: Event)

    /**
     * Returns the current state of the reducer.
     */
    suspend fun currentState(): State
}
