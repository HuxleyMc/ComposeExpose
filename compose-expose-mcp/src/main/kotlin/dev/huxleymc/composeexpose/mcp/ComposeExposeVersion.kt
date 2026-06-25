package dev.huxleymc.composeexpose.mcp

import java.io.InputStream
import java.util.Properties

private const val VERSION_RESOURCE = "compose-expose-version.properties"
private const val DEVELOPMENT_VERSION = "dev"

internal fun resolveComposeExposeServerVersion(
    resourceLoader: (String) -> InputStream? = { resource ->
        Thread.currentThread().contextClassLoader?.getResourceAsStream(resource)
            ?: ComposeExposeService::class.java.classLoader.getResourceAsStream(resource)
    },
): String {
    val properties =
        resourceLoader(VERSION_RESOURCE)?.use { stream ->
            Properties().apply { load(stream) }
        } ?: return DEVELOPMENT_VERSION

    return properties.getProperty("version")?.takeIf { it.isNotBlank() } ?: DEVELOPMENT_VERSION
}
