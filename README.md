# Kresil - Kotlin Resilience Library <img src="./docs/imgs/kresil-logo.png" align="right" width=200 alt="" />

Kresil is a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) library for fault-tolerance,
inspired by [Resilience4j](https://resilience4j.readme.io/docs/getting-started) for Java
and [Polly](https://github.com/App-vNext/Polly) for .NET. The library offers methods to enhance operations with
resilience mechanisms in a functional style, using higher-order functions (decorators) while providing a concise API.
Additionally, Kresil offers extensions for [Ktor](https://ktor.io/) as plugins.

## Resilience Mechanisms

- 🔁 [Retry](#retry): Repeats failed executions;
- ⛔ [Circuit Breaker](#circuit-breaker): Temporarily blocks possible failures (🚧).

> [!NOTE]
> The symbol 🚧 means that the mechanism is under development.

## Modules

- 📁 [kresil-lib](kresil-lib/lib/README.md): Core module with the resilience mechanisms;
- 📁 [ktor-client-plugins](ktor-client-plugins/shared/README.md): Mechanisms integration for Ktor Client;
- 📁 [ktor-server-plugins](ktor-server-plugins/shared/README.md): Mechanisms integration for Ktor Server.
- 📁 [beta-demo](beta-demo/README.md): Applications for resilience mechanisms demonstration.

## Retry

The [Retry](https://learn.microsoft.com/en-us/azure/architecture/patterns/retry)
is a reactive resilience mechanism
that can be used to retry an operation when it fails, and the failure is a transient error.
Operations can be decorated and executed on demand.
A retry mechanism is initialized with a configuration that,
through pre-configured policies, defines its behaviour.

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
        maxAttempts = 5
        addRetryPredicate { it is NetworkError }
        retryOnResultIf { it is "success" }
        constantDelay(500.milliseconds)
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
retry.onRetry { event -> println(event) }

// listen to all events
retry.onEvent { event -> println(event) }

// cancel all listeners
retry.cancelListeners()
```

## Circuit Breaker

The [Circuit Breaker](https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker)
is a reactive resilience mechanism
that can be used to protect a system component from overloading or failing.
By monitoring the health of the system, the circuit breaker can short-circuit
execution requests when it detects that the system component is not behaving as expected.
After a timeout,
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

### Usage

```kotlin
// use predefined policies
val defaultCircuitBreaker = CircuitBreaker()

// use custom policies
val circuitBreaker = CircuitBreaker(
    circuitBreakerConfig {
        failureRateThreshold = 0.5
        constantDelayInOpenState(500.milliseconds)
        recordResultPredicate { it is "success" }
        recordExceptionPredicate { it is NetworkError }
        slidingWindow(size = 5, minimumThroughput = 2, type = COUNT_BASED)
    }
)

// wire the circuit breaker
circuitBreaker.wire()

// execute an operation under the circuit breaker
val result = circuitBreaker.executeOperation {
    // operation
}

// listen to specific events
circuitBreaker.onCallNotPermitted {
    // action
}

// listen to all events
circuitBreaker.onEvent {
    // action
}

// cancel all registered listeners
circuitBreaker.cancelListeners()
```
