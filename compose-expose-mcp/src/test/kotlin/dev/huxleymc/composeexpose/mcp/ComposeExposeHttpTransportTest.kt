package dev.huxleymc.composeexpose.mcp

import dev.huxleymc.composeexpose.core.ComposableDeclaration
import dev.huxleymc.composeexpose.core.ComposableIndex
import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.IndexMetadata
import dev.huxleymc.composeexpose.core.SourceLocation
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttpTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.ktor.server.cio.CIO as ServerCIO

class ComposeExposeHttpTransportTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `streamable http client can call compose search tool`() =
        runBlocking {
            withTimeout(30_000) {
                assertHttpSearchToolWorks()
            }
        }

    private suspend fun assertHttpSearchToolWorks() {
        val indexFile = tempDir.resolve("build/composeExpose/all-composables.json")
        indexFile.parent.createDirectories()
        indexFile.writeText(ComposableIndexJson.encode(sampleIndex()))

        val service = ComposeExposeService(projectRoot = tempDir, indexFile = indexFile)
        val port = findFreePort()
        val engine =
            embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
                installComposeExposeHttpTransport(service)
            }.start(wait = false)
        val httpClient = HttpClient(CIO)
        val mcpClient = Client(clientInfo = Implementation(name = "http-test-client", version = "1.0"))
        try {
            val transport = httpClient.mcpStreamableHttpTransport("http://127.0.0.1:$port/mcp")
            mcpClient.connect(transport)

            val tools = mcpClient.listTools()
            assertTrue(tools.tools.any { it.name == "search_composables" })

            val result = mcpClient.callTool("search_composables", mapOf("query" to "HttpCard", "limit" to 1))
            val text = (result.content.firstOrNull() as? TextContent)?.text

            assertNotNull(text)
            assertTrue(text.contains("HttpCard"))
        } finally {
            mcpClient.close()
            httpClient.close()
            engine.stop()
        }
    }

    private fun sampleIndex(): ComposableIndex =
        ComposableIndex(
            metadata =
                IndexMetadata(
                    generatedAtEpochMillis = 1L,
                    projectRoot = tempDir.toString(),
                    modules = listOf(":app"),
                    sourceRoots = emptyList(),
                ),
            composables =
                listOf(
                    ComposableDeclaration(
                        id = ":app:main:dev.example.HttpCard#",
                        module = ":app",
                        sourceSet = "main",
                        packageName = "dev.example",
                        name = "HttpCard",
                        visibility = "public",
                        source = SourceLocation("app/src/main/kotlin/dev/example/HttpCard.kt", 1, 1),
                        kdoc = null,
                        parameters = emptyList(),
                        annotations = listOf("@Composable"),
                        previews = emptyList(),
                    ),
                ),
        )

    private fun findFreePort(): Int =
        ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            socket.localPort
        }
}
