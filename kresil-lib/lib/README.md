# Kresil Mechanisms

This module provides the core resilience mechanisms.

## Mechanisms

| Mechanism         | Implementation                                                                     | Tests                                                         |
|-------------------|------------------------------------------------------------------------------------|---------------------------------------------------------------|
| 🔁 Retry          | [Retry.kt](src/commonMain/kotlin/kresil/retry/Retry.kt)                            | [RetryTest.kt](src/commonTest/kotlin/retry)                   |
| ⚡ Circuit Breaker | [CircuitBreaker.kt](src/commonMain/kotlin/kresil/circuitbreaker/CircuitBreaker.kt) | [CircuitBreakerTest.kt](src/commonTest/kotlin/circuitbreaker) |
