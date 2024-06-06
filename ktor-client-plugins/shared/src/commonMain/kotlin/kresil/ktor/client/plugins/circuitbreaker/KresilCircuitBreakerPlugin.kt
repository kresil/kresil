package kresil.ktor.client.plugins.circuitbreaker

import io.ktor.client.plugins.api.*
import io.ktor.util.logging.*
import kresil.circuitbreaker.CircuitBreaker
import kresil.circuitbreaker.config.circuitBreakerConfig
import kresil.circuitbreaker.state.reducer.CircuitBreakerReducerEvent.*
import kresil.ktor.client.plugins.circuitbreaker.config.CircuitBreakerPluginConfig
import kresil.ktor.client.plugins.circuitbreaker.config.CircuitBreakerPluginConfigBuilder

private val logger = KtorSimpleLogger("kresil.ktor.client.plugins.circuitbreaker.KresilCircuitBreakerPlugin")

val KresilCircuitBreakerPlugin = createClientPlugin(
    name = "KresilCircuitBreakerPlugin",
    createConfiguration = {
        CircuitBreakerPluginConfigBuilder(baseConfig = defaultCircuitBreakerPluginConfig)
    }
) {
    val pluginConfig = pluginConfig.build()
    val circuitBreakerConfig = pluginConfig.circuitBreakerConfig
    val circuitBreaker = CircuitBreaker(config = circuitBreakerConfig)

    onRequest { request, _ ->
        // asks for permission to proceed with the request
        logger.info("Requesting permission to proceed with the request")
        circuitBreaker.wire()
        logger.info("Permission granted")
    }

    onResponse { response ->
        // Record success or failure after the response
        if (pluginConfig.recordResponseAsFailurePredicate(response)) {
            logger.info("Recording response as failure")
            circuitBreaker.stateReducer.dispatch(OPERATION_FAILURE)
        } else {
            logger.info("Recording response as success")
            circuitBreaker.stateReducer.dispatch(OPERATION_SUCCESS)
        }
    }
}

private val defaultCircuitBreakerPluginConfig = CircuitBreakerPluginConfig(
    circuitBreakerConfig = circuitBreakerConfig {},
        recordResponseAsFailurePredicate = { response -> response.status.value in 500..599 }
)