package kresil.core.builders

/**
 * Generic function to create a configuration instance for a specified mechanism.
 * @param builder The builder instance used to construct the final configuration.
 * @param configure The configuration to be applied to the builder before creating the final configuration.
 */
internal fun <TBuilder: ConfigBuilder<TConfig>, TConfig> mechanismConfigBuilder(
    builder: TBuilder,
    configure: TBuilder.() -> Unit
): TConfig = builder.apply(configure).build()
