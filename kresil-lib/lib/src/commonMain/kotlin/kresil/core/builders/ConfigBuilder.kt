package kresil.core.builders

/**
 * An interface for building configuration objects.
 * It specifies the following behaviour:
 * - ``config(default)`` -> ``configBuilder`` -> ``config`` -> ``configBuilder`` -> ...
 *
 * Which allows for potentially incremental configuration for override purposes and maintain already defined configurations.
 * @param T the type of the configuration object to be built
 */
interface ConfigBuilder<T> {
    /**
     * The base configuration object to be used as a starting point for building the final configuration object.
     */
    val baseConfig: T

    /**
     * Builds the final configuration object after applying possible modifications.
     */
    fun build(): T
}
