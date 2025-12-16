# Writing Domain Use Cases

Use cases are composable, invokable functions written to implement domain logic.
This domain logic can take the form

`UseCase` objects provide a layer of insulation between UI and

With few exceptions, any use case you implement must use interface parameters.
This means that a `UseCase` must not accept use the type of a repository
implementation as a constructor parameter.


## Structure

A basic `UseCase` may look something like this:

```kotlin
class FetchThingUseCase(
    private val repository: ThingRepository,
) {
    suspend fun invoke(thingId: String) {
        return repository.getThing(thingId)
    }
}
```

For application consistency,the only public symbols accessible from a use case
are its one or more `invoke` methods.

Do not do something like this:

```kotlin
@Suppress("RedundantSuspendModifier")
class FetchThingUseCase(
    private val repository: ThingRepository,
) {
    suspend fun fetchWithId(thingId: String) {
        return repository.getThing(thingId)
    }
    
    suspend fun fetchWithIntId(thingId: Int) {
        return repository.getThing(thingId)
    }
}
```

A use case is not a manager. Its state depends solely on that of its inputs.

### Observable Use Cases

It's likely that you'll want to observe some data from a client. `UseCase`
functions use Kotlin's `Flow` to handle this.

```kotlin
class ObserveThingUseCase(
    private val repository: ThingRepository,
) {
    fun invoke(thingId: String) {
        repository.observeThing(thingId)
    }
}
```

It is an anti-pattern to create a `UseCase` with an invoke function that both is
a `suspend` function and returns an observable `Flow`. Do not do this as it will
confuse downstream API consumers.