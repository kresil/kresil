package kresil.core.builders

/**
 * Defines a builder for configuration objects.
 * It allows for incremental configuration by providing a base configuration object and applying modifications to it.
 * - ``config(default/initial)`` -> ``configBuilder`` -> ``config`` -> ``configBuilder`` -> ``config`` -> ...
 *
 * Which allows for potentially incremental configuration for override purposes and maintain already defined configurations.
 * @param T the type of the configuration object to be built
 */
interface ConfigBuilder<T> {
    /**
     * The base configuration object to be used as a starting point for building the final configuration object.
     * @see build
     */
    val baseConfig: T

    /**
     * Builds the final configuration object after applying possible modifications to the base configuration.
     * @see baseConfig
     */
    fun build(): T
}
