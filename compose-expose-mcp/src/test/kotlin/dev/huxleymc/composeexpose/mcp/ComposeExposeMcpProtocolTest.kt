package dev.huxleymc.composeexpose.mcp

import dev.huxleymc.composeexpose.core.ComposableDeclaration
import dev.huxleymc.composeexpose.core.ComposableIndex
import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.IndexMetadata
import dev.huxleymc.composeexpose.core.SourceLocation
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalMcpApi::class)
class ComposeExposeMcpProtocolTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `client can discover and call compose search tool`() = runTest {
        val indexFile = tempDir.resolve("build/composeExpose/all-composables.json")
        indexFile.parent.createDirectories()
        indexFile.writeText(ComposableIndexJson.encode(sampleIndex()))

        val server = buildComposeExposeMcpServer(ComposeExposeService(tempDir, indexFile))
        val client = Client(clientInfo = Implementation(name = "test-client", version = "1.0"))
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        val serverSession = CompletableDeferred<ServerSession>()

        try {
            val clientJob = launch { client.connect(clientTransport) }
            val serverJob = launch { serverSession.complete(server.createSession(serverTransport)) }
            clientJob.join()
            serverJob.join()

            val tools = client.listTools()
            assertTrue(tools.tools.any { it.name == "search_composables" })

            val result = client.callTool("search_composables", mapOf("query" to "account"))

            val text = (result.content.firstOrNull() as? TextContent)?.text
            assertNotNull(text)
            assertTrue(text.contains("AccountCard"))
        } finally {
            client.close()
            server.close()
        }
    }

    private fun sampleIndex(): ComposableIndex {
        return ComposableIndex(
            metadata = IndexMetadata(
                generatedAtEpochMillis = 1L,
                projectRoot = tempDir.toString(),
                modules = listOf(":app"),
                sourceRoots = emptyList(),
            ),
            composables = listOf(
                ComposableDeclaration(
                    id = ":app:main:dev.example.AccountCard#",
                    module = ":app",
                    sourceSet = "main",
                    packageName = "dev.example",
                    name = "AccountCard",
                    visibility = "public",
                    source = SourceLocation("app/src/main/kotlin/dev/example/Cards.kt", 1, 1),
                    kdoc = null,
                    parameters = emptyList(),
                    annotations = listOf("@Composable"),
                    previews = emptyList(),
                ),
            ),
        )
    }
}
