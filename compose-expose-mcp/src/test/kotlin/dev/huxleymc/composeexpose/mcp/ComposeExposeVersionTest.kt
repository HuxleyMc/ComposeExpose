package dev.huxleymc.composeexpose.mcp

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeExposeVersionTest {
    @Test
    fun `resolves server version from generated resource`() {
        val version =
            resolveComposeExposeServerVersion {
                "version=9.8.7\n".byteInputStream()
            }

        assertEquals("9.8.7", version)
    }

    @Test
    fun `falls back to development version when generated resource is missing`() {
        val version = resolveComposeExposeServerVersion { null }

        assertEquals("dev", version)
    }
}
