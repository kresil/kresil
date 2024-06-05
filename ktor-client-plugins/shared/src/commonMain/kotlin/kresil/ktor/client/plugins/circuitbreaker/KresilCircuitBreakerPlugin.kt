package kresil.ktor.client.plugins.circuitbreaker

import io.ktor.client.plugins.api.*
import io.ktor.util.logging.*
import kresil.circuitbreaker.config.circuitBreakerConfig
import kresil.ktor.client.plugins.circuitbreaker.config.CircuitBreakerPluginConfig
import kresil.ktor.client.plugins.circuitbreaker.config.CircuitBreakerPluginConfigBuilder

private val logger = KtorSimpleLogger("kresil.ktor.client.plugins.circuitbreaker.KresilCircuitBreakerPlugin")

val KresilCircuitBreakerPlugin = createClientPlugin(
    name = "KresilCircuitBreakerPlugin",
    createConfiguration = {
        CircuitBreakerPluginConfigBuilder(baseConfig = defaultCircuitBreakerPluginConfig)
    }
) {

}

private val defaultCircuitBreakerPluginConfig = CircuitBreakerPluginConfig(
    circuitBreakerConfig = circuitBreakerConfig {
        // TODO: add default circuit breaker configuration
    },
)
