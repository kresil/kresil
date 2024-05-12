package kresil.circuitbreaker.config

import kresil.core.builders.ConfigBuilder
import kotlin.time.Duration.Companion.seconds

class CircuitBreakerConfigBuilder(
    override val baseConfig: CircuitBreakerConfig = defaultCircuitBreakerConfig
) : ConfigBuilder<CircuitBreakerConfig> {

    override fun build(): CircuitBreakerConfig {
        TODO("Not yet implemented")
    }

}

private val defaultCircuitBreakerConfig = CircuitBreakerConfig(
    failureRateThreshold = 0.5, // between 0.0 and 1.0
    slidingWindowSize = 10,
    minimumThroughput = 10,
    permittedNumberOfCallsInHalfOpenState = 10,
    waitDurationInOpenState = 1.seconds,
    recordFailurePredicate = { true },
    recordSuccessAsFailurePredicate = { false },
    ignoreFailurePredicate = { true },
)
