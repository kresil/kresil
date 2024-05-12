package kresil.core.builders

/**
 * Generic function to create a configuration instance for a specified mechanism.
 * @param builder The builder instance to be used to create the configuration.
 * @param configure The configuration to be applied to the builder to create the configuration.
 */
fun <Builder: ConfigBuilder<Config>, Config> mechanismConfigBuilder(
    builder: Builder,
    configure: Builder.() -> Unit
): Config = builder.apply(configure).build()
