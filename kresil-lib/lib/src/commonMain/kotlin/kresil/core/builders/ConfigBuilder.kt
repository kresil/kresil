package kresil.core.builders

/**
 * Defines a builder for configuration objects.
 * It allows for incremental configuration and override by providing a base configuration object and applying modifications to it,
 *
 * The configuration process is as follows:
 * 1. Begin with a default or initial (base) configuration object;
 * 2. Pass this configuration to a configuration builder;
 * 3. The builder potentially modifies the base configuration;
 * 4. Generate a new configuration from the builder;
 * 5. If further modifications are needed, pass the new configuration back to the builder, which will use it as the base configuration;
 * 6. Repeat the process until the desired configuration is achieved.
 *
 * @param TConfig the type of the configuration object to be built
 */
interface ConfigBuilder<TConfig> {
    /**
     * The base configuration object to be used as a starting point for building the final configuration object.
     * @see build
     */
    val baseConfig: TConfig

    /**
     * Builds the final configuration object after applying possible modifications to the base configuration.
     * @see baseConfig
     */
    fun build(): TConfig
}
