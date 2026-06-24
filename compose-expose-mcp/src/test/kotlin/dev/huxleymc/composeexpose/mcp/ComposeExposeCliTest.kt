package dev.huxleymc.composeexpose.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Path

class ComposeExposeCliTest {
    @Test
    fun `defaults to stdio transport and aggregate index`() {
        val options = ComposeExposeCli.parse(emptyArray(), defaultProjectRoot = Path.of("/repo"))

        assertEquals(ServerTransport.Stdio, options.transport)
        assertEquals(Path.of("/repo"), options.projectRoot)
        assertEquals(Path.of("/repo/build/composeExpose/all-composables.json"), options.indexFile)
    }

    @Test
    fun `parses http transport port and explicit index`() {
        val options = ComposeExposeCli.parse(
            arrayOf(
                "--project-root", "/repo",
                "--index-file", "/repo/custom.json",
                "--transport", "http",
                "--port", "8123",
            ),
            defaultProjectRoot = Path.of("/ignored"),
        )

        assertEquals(ServerTransport.Http, options.transport)
        assertEquals(8123, options.port)
        assertEquals(Path.of("/repo/custom.json"), options.indexFile)
    }

    @Test
    fun `rejects unknown arguments`() {
        assertFailsWith<IllegalArgumentException> {
            ComposeExposeCli.parse(arrayOf("--wat"), defaultProjectRoot = Path.of("/repo"))
        }
    }
}
