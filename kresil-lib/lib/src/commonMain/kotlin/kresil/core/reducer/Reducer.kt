package kresil.core.reducer

internal interface Reducer<State, Event> {
    suspend fun dispatch(event: Event)
    suspend fun currentState(): State
}
