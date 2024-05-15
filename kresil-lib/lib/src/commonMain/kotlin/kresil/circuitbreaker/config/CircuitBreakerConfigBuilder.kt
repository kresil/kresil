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
    failureRateThreshold = 0.5,
    slidingWindowSize = 10,
    minimumThroughput = 10,
    permittedNumberOfCallsInHalfOpenState = 10,
    waitDurationInOpenState = 60.seconds,
    waitDurationInHalfOpenState = 25.seconds,
    recordExceptionPredicate = { true },
    recordSuccessAsFailurePredicate = { false },
)
