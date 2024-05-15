package kresil.core.builders

/**
 * Generic function to create a configuration instance for a specified mechanism.
 * @param builder The builder instance used to construct the final configuration.
 * @param configure The configuration to be applied to the builder before creating the final configuration.
 */
internal fun <Builder: ConfigBuilder<Config>, Config> mechanismConfigBuilder(
    builder: Builder,
    configure: Builder.() -> Unit
): Config = builder.apply(configure).build()
