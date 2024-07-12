package kresil.ktor.client.plugins.circuitbreaker.config

import io.ktor.client.statement.*
import kresil.ktor.client.plugins.circuitbreaker.KresilCircuitBreakerPlugin
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.CircuitBreaker

/**
 * A predicate that determines whether an HTTP response should be recorded as a failure.
 * @return `true` if the response should be recorded as a failure, `false` otherwise.
 */
internal typealias RecordResponsePredicate = (HttpResponse) -> Boolean

/**
 * Configuration for the [KresilCircuitBreakerPlugin].
 * @param circuitBreakerConfig The configuration for the Kresil [CircuitBreaker] mechanism.
 * @param recordResponseAsFailurePredicate A predicate that determines whether an HTTP response should be recorded as a failure.
 */
data class CircuitBreakerPluginConfig(
    val circuitBreakerConfig: CircuitBreakerConfig,
    val recordResponseAsFailurePredicate: RecordResponsePredicate,
)
