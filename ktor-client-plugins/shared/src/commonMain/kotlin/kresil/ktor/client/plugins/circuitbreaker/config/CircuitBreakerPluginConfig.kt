package kresil.ktor.client.plugins.circuitbreaker.config

import kresil.ktor.client.plugins.circuitbreaker.KresilCircuitBreakerPlugin
import kresil.circuitbreaker.config.CircuitBreakerConfig
import kresil.circuitbreaker.CircuitBreaker

/**
 * Configuration for the [KresilCircuitBreakerPlugin].
 * @param circuitBreakerConfig The configuration for the Kresil [CircuitBreaker] mechanism.
 */
data class CircuitBreakerPluginConfig(
    val circuitBreakerConfig: CircuitBreakerConfig,
)
