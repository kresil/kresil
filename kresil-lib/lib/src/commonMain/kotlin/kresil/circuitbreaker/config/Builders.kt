package kresil.circuitbreaker.config

import kresil.circuitbreaker.CircuitBreaker
import kresil.core.builders.mechanismConfigBuilder

/**
 * Creates a [CircuitBreakerConfig] instance using the provided configuration.
 * @see [CircuitBreaker]
 */
fun circuitBreakerConfig(configure: CircuitBreakerConfigBuilder.() -> Unit): CircuitBreakerConfig =
    mechanismConfigBuilder(CircuitBreakerConfigBuilder(), configure)

/**
 * Creates a [CircuitBreakerConfig] instance using the provided configuration which will be based on the received configuration.
 * @see [CircuitBreaker]
 */
fun circuitBreakerConfig(
    baseConfig: CircuitBreakerConfig,
    configure: CircuitBreakerConfigBuilder.() -> Unit,
): CircuitBreakerConfig = mechanismConfigBuilder(CircuitBreakerConfigBuilder(baseConfig), configure)

/**
 * Creates a [CircuitBreakerConfig] instance using the default configuration.
 * @see [CircuitBreaker]
 */
fun defaultCircuitBreakerConfig(): CircuitBreakerConfig = circuitBreakerConfig {}
