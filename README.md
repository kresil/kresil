# Kresil - Kotlin Resilience Library <img src="./docs/imgs/kresil-logo.png" align="right" width=200 alt="" />

Kresil is a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) library for fault-tolerance,
inspired by [Resilience4j](https://resilience4j.readme.io/docs/getting-started) for Java
and [Polly](https://github.com/App-vNext/Polly) for .NET. The library offers methods to enhance operations with
resilience mechanisms in a functional style, using higher-order functions (decorators) while providing a concise API.
Additionally, Kresil offers extensions for [Ktor](https://ktor.io/) as plugins.

## Resilience Mechanisms

- 🔁 [Retry](#retry): Repeats failed executions;
- ⛔ [Circuit Breaker](#circuit-breaker): Temporarily blocks possible failures.
- ⏳ [Rate Limiter](#rate-limiter): Limits executions per period (🚧).

> [!NOTE]
> The symbol 🚧 means that the mechanism is under development.

## Relevant Modules

- 📁 [kresil-lib](kresil-lib/lib/README.md): Core module with the resilience mechanisms;
- 📁 [ktor-client-plugins](ktor-client-plugins/shared/README.md): Mechanisms integration for [Ktor Client](https://ktor.io/docs/client-create-new-application.html) as plugins;
- 📁 [ktor-server-plugins](ktor-server-plugins/shared/README.md): Mechanisms integration for [Ktor Server](https://ktor.io/docs/server-create-a-new-project.html) as plugins;
- 📁 [ktor-plugins-demo](ktor-plugins-demo/README.md): Kotlin/JS applications for demonstrating the mechanisms, namely its integration with Ktor.

## Retry

A [Retry](https://learn.microsoft.com/en-us/azure/architecture/patterns/retry) is a reactive resilience mechanism
that can be used to retry an operation when it fails and the failure is a transient (temporary) fault. 
Operations can be decorated and executed on demand.
A retry mechanism is initialized with a configuration that,
through pre-configured policies, define its behaviour.

### State Machine

The retry mechanism implements the following state machine:

```
                   +------------------+  retried once   +---------+
+-----------+ ---> | Returns Normally | --------------> | Success |
| Operation |      +------------------+                 +---------+
|  Called   |      +-------+      +----------+
+-----------+ ---> | Fails | ---> | Consults |
      ^            +-------+      | Policies |
      |                           +----------+
      |                                |
  +-------+      can use retry         |
  | Retry | <--------------------------|
  +-------+                            |   expected
                                       |   failure    +-------+
                                       |------------> | Error |
                                       |              +-------+
                    +---------+        |
                    | Ignored | <------|
                    |  Error  |
                    +---------+
```

### Usage

```kotlin
// use predefined retry policies
val defaultRetry = Retry()

// use custom policies
val retry = Retry(
    retryConfig {
        maxAttempts = 5 // initial call + 4 retries
        retryIf { it is NetworkError }
        retryOnResult { it is "success" }
        exponentialDelay(initialDelay = 500L.milliseconds, multiplier = 1.0, maxDelay = 1.minutes, randomizationFactor = 0.1)
        // customDelay { attempt, context -> ... }
        // exceptionHandler { exception -> ... }
    }
)

// execute a supplier
retry.executeSupplier {
    // operation
}

// execute a supplier with context
retry.executeCtxSupplier { ctx ->
    // operation
}

// decorate a supplier
val decoratedSupplier = retry.decorateSupplier {
    // operation
}
// and call it later
val result = decoratedSupplier()

// listen to specific events
retry.onRetry { event ->
    // action
}

// listen to all events
retry.onEvent { event ->
    // action
}

// cancel all listeners
retry.cancelListeners()
```

## Circuit Breaker

The [Circuit Breaker](https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker)
is a **reactive** resilience mechanism
that can be used to protect a system component from overloading or failing.
By monitoring the health of the system, the circuit breaker can short-circuit
execution requests when it detects that the system component is not behaving as expected.
After a configurable timeout,
the circuit breaker allows a limited number of test requests to pass through to see if the system has recovered.
Depending on the test results, the circuit breaker can resume normal operation or continue to short-circuit requests.
A circuit breaker is initialized with a configuration that,
through pre-configured policies, define its behaviour.

### State Machine

The circuit breaker implements the following state machine:

```
             failure rate exceeds
 +--------+  or equals threshold   +------+
 | Closed | ---------------------> | Open |
 +--------+                        +------+
     ^                               |  ^
     |                         after |  |  failure rate
     |                       timeout |  |  exceeds or
     |                               |  |  equals threshold
     |       failure rate            v  |
     |       below threshold     +-----------+
     |-------------------------- | Half-Open |
                                 +-----------+
```

- In the **Closed** state, the circuit breaker allows calls to execute the underlying operation, while
recording the success or failure of these calls.
When the failure rate exceeds a (configurable) threshold, the
circuit breaker transitions to the **Open** state.
- In the **Open** state,
the circuit breaker rejects all received calls for a (configurable) amount of time and then transitions
to the **HalfOpen** state.
- In the **HalfOpen** state,
the circuit breaker allows a (configurable) number of calls to test if the underlying operation is still failing.
After all calls have been attempted, the circuit breaker transitions back to the **Open** state if newly calculated
failure rate exceeds or equals the threshold; otherwise, it transitions to the **Closed** state.

### Usage

```kotlin
// use predefined policies
val defaultCircuitBreaker = CircuitBreaker()

// use custom policies
val circuitBreaker = CircuitBreaker(
    circuitBreakerConfig {
        failureRateThreshold = 0.5 // 50%
        recordResultPredicate { it is "success" }
        recordExceptionPredicate { it is NetworkError }
        exponentialDelayInOpenState(initialDelay = 30.seconds, multiplier = 2.0, maxDelay = 10.minutes)
        slidingWindow(size = 5, minimumThroughput = 2, type = COUNT_BASED)
    }
)

// get the current state of the circuit breaker
val observedState = circuitBreaker.currentState()

// wire the circuit breaker
circuitBreaker.wire()

// execute an operation under the circuit breaker
val result = circuitBreaker.executeOperation {
    // operation
}

// listen to specific events
circuitBreaker.onCallNotPermitted { event ->
    // action
}

// listen to all events
circuitBreaker.onEvent { event ->
    // action
}

// cancel all registered listeners
circuitBreaker.cancelListeners()

// manually:
// - override the circuit breaker state
circuitBreaker.transitionToOpen()
// - reset the circuit breaker
circuitBreaker.reset()
// - record an operation success
circuitBreaker.recordSuccess()
// - record an operation failure
circuitBreaker.recordFailure()
```

## Rate Limiter

The [Rate Limiter](https://learn.microsoft.com/en-us/azure/architecture/patterns/rate-limiting) is a **proactive** resilience mechanism
that can be used to limit the number of requests that can be made to a system component, thereby controlling the consumption of resources and protecting the system from overloading.
A rate limiter is initialized with a configuration that, through pre-configured policies, defines its behaviour.
