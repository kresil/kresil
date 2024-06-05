package kresil.ktor.client.plugins.circuitbreaker.config

import kresil.circuitbreaker.config.CircuitBreakerConfigBuilder
import kresil.core.builders.ConfigBuilder
import kresil.ktor.client.plugins.circuitbreaker.KresilCircuitBreakerPlugin

/**
 * Builder for configuring the [KresilCircuitBreakerPlugin].
 */
class CircuitBreakerPluginConfigBuilder(override val baseConfig: CircuitBreakerPluginConfig) : ConfigBuilder<CircuitBreakerPluginConfig> {

    private val cbreakerConfigBuilder: CircuitBreakerConfigBuilder = CircuitBreakerConfigBuilder(baseConfig.circuitBreakerConfig)

    override fun build(): CircuitBreakerPluginConfig {
        TODO("Not yet implemented")
    }

}
