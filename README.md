# Kresil - Kotlin Resilience Library <img src="./docs/imgs/kresil-logo.png" align="right" width=200 alt="" />

Kresil is a lightweight, easy-to-use,
[fault tolerance](https://en.wikipedia.org/wiki/Fault_tolerance) library designed to help Kotlin developers build
resilient applications in Kotlin Multiplatform.

Kresil provides higher-order functions (decorators) to enhance any functional interface, lambda expression,
or method reference with a Circuit Breaker, Rate Limiter, Retry, Bulkhead, or Time Limiter.

Additionally, Kresil offers extensions for [Ktor](https://ktor.io/), seamlessly integrating fault tolerance features
into the popular framework.

It is heavily inspired by the [Resilience4j](https://resilience4j.readme.io/docs/getting-started) library for Java.

### Intended Resilience Strategies

| name                | how does it work?                                                                 | description                                                                               | links             |
|---------------------|-----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|-------------------|
| **Retry**           | Repeats failed executions                                                         | Many faults are transient and may self-correct after a short delay.                       | [Documentation]() |
| **Circuit Breaker** | Temporary blocks possible failures                                                | When a system is seriously struggling, failing fast is better than making clients wait.   | [Documentation]() |
| **Rate Limiter**    | Limits executions/period                                                          | Limit the rate of incoming requests.                                                      | [Documentation]() |
| **Time Limiter**    | Limits duration of execution                                                      | Beyond a certain wait interval, a successful result is unlikely.                          | [Documentation]() |
| **Bulkhead**        | Limits concurrent executions                                                      | Resources are isolated into pools so that if one fails, the others will continue working. | [Documentation]() |
| **Cache**           | Memorizes a successful result                                                     | Some proportion of requests may be similar.                                               | [Documentation]() |
| **Fallback**        | Defines an alternative value to be returned (or action to be executed) on failure | Things will still fail - plan what you will do when that happens.                         | [Documentation]() |

_The above table is based on [resiliency4j](https://github.com/resilience4j/resilience4j/tree/master?tab=readme-ov-file#resilience-patterns) documentation_

### Proactive vs Reactive Resilience

| Resilience           | Definition                                  | Strategies                       |
|----------------------|---------------------------------------------|----------------------------------|
| Reactive Resilience  | React to failures and mitigate their impact | Retry, Circuit Breaker, Fallback |
| Proactive Resilience | Prevent failures from happening             | Time Limiter, Rate Limiter       |

_The above table is based on [Polly](https://github.com/App-vNext/Polly#resilience-strategies) documentation_