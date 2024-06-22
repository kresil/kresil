package kresil.core.reducer

/**
 * Similiar to React's [useReducer](https://react.dev/reference/react/useReducer)
 * hook, this contract defines a reducer that can be used to manage the state of a component.
 * The [reducer] method is used to reduce an event to a new state and potentially a list of effects.
 * This effect can be used to trigger side-effects, outside of the reducer, such as network requests or
 * updating the UI.
 * A reducer should be used to manage the state of a component in a deterministic way (i.e., given the same
 * sequence of events, the reducer should always produce the same state - pure function).
 *
 * To alter the internal state of a component, callers can use [dispatch] to emit events.
 * The current state can be consulted using the [currentState] method.
 * @param State the type of the state managed by the reducer.
 * @param Event the type of the event that triggers a state transition.
 * @param Effect the type of the effect emitted by the reducer.
 */
abstract class Reducer<State, Event, Effect> {

    /**
     * Dispatches an event to the reducer to trigger a state transition.
     * @param event the event to dispatch.
     */
    abstract suspend fun dispatch(event: Event)

    /**
     * Returns the current state of the reducer.
     */
    abstract suspend fun currentState(): State

    /**
     * Given the current state and an event, reduces the event to a new state and a list of effects.
     * @param state the current state.
     * @param event the event to reduce.
     */
    protected abstract suspend fun reducer(
        state: State,
        event: Event,
    ) : Pair<State, List<Effect>>

}
