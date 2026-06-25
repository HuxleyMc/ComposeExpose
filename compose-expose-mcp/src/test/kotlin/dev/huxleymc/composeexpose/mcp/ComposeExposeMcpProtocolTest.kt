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
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalMcpApi::class)
class ComposeExposeMcpProtocolTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `client can discover and call compose search tool`() =
        runTest {
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

                val serverInstructions = assertNotNull(client.serverInstructions)
                assertTrue(serverInstructions.contains("index_status"))
                assertTrue(serverInstructions.contains("refresh_index"))
                assertTrue(serverInstructions.contains("structuredContent"))

                val tools = client.listTools()
                val searchTool = assertNotNull(tools.tools.firstOrNull { it.name == "search_composables" })
                val searchOutputSchema = searchTool.outputSchema ?: error("search_composables outputSchema was missing")
                val searchOutputProperties = assertNotNull(searchOutputSchema.properties)
                assertEquals(
                    "array",
                    searchOutputProperties["results"]
                        ?.jsonObject
                        ?.get("type")
                        ?.jsonPrimitive
                        ?.content,
                )
                val resultItemProperties =
                    assertNotNull(
                        searchOutputProperties["results"]
                            ?.jsonObject
                            ?.get("items")
                            ?.jsonObject
                            ?.get("properties")
                            ?.jsonObject,
                    )
                assertEquals("string", schemaPropertyType(resultItemProperties, "id"))
                assertEquals("string", schemaPropertyType(resultItemProperties, "name"))
                assertEquals("object", schemaPropertyType(resultItemProperties, "source"))
                assertEquals(
                    "string",
                    nestedSchemaPropertyType(resultItemProperties, "source", "file"),
                )
                assertEquals(
                    "boolean",
                    nestedArrayItemSchemaPropertyType(resultItemProperties, "parameters", "hasDefault"),
                )
                assertEquals(
                    "string",
                    nestedArrayItemSchemaPropertyType(resultItemProperties, "previews", "annotation"),
                )

                val statusProperties = toolResultProperties(tools.tools, "index_status", "status")
                assertEquals("boolean", schemaPropertyType(statusProperties, "exists"))
                assertEquals("boolean", schemaPropertyType(statusProperties, "isStale"))
                assertEquals(listOf("integer", "null"), anyOfSchemaPropertyTypes(statusProperties, "ageMillis"))
                assertEquals("array", schemaPropertyType(statusProperties, "newerSources"))
                assertEquals("string", arrayItemSchemaType(statusProperties, "newerSources"))
                assertEquals("boolean", schemaPropertyType(statusProperties, "refreshInProgress"))

                val refreshProperties = toolResultProperties(tools.tools, "refresh_index", "result")
                assertEquals("boolean", schemaPropertyType(refreshProperties, "success"))
                assertEquals("string", schemaPropertyType(refreshProperties, "output"))
                assertEquals("object", schemaPropertyType(refreshProperties, "status"))
                assertEquals("boolean", nestedSchemaPropertyType(refreshProperties, "status", "exists"))

                val result = client.callTool("search_composables", mapOf("query" to "AccountCard", "limit" to 1))

                val text = (result.content.firstOrNull() as? TextContent)?.text
                assertNotNull(text)
                assertTrue(text.contains("AccountCard"))
                assertEquals(1, Regex("\"name\"").findAll(text).count())

                val structuredContent = assertNotNull(result.structuredContent)
                val structuredResults = assertNotNull(structuredContent["results"]).jsonArray
                assertEquals(
                    "AccountCard",
                    structuredResults
                        .single()
                        .jsonObject["name"]
                        ?.jsonPrimitive
                        ?.content,
                )

                val modules =
                    client.readResource(
                        ReadResourceRequest(ReadResourceRequestParams(uri = "compose-expose://modules")),
                    )
                val modulesText = (modules.contents.firstOrNull() as? TextResourceContents)?.text
                assertNotNull(modulesText)
                assertTrue(modulesText.contains("\"composableCount\""))
                assertTrue(modulesText.contains("\"packages\""))

                val resourceTemplates = client.listResourceTemplates(ListResourceTemplatesRequest())
                assertTrue(resourceTemplates.resourceTemplates.any { it.uriTemplate == "compose-expose://module/{module}" })

                val module =
                    client.readResource(
                        ReadResourceRequest(ReadResourceRequestParams(uri = "compose-expose://module/:app")),
                    )
                val moduleText = (module.contents.firstOrNull() as? TextResourceContents)?.text
                assertNotNull(moduleText)
                assertTrue(moduleText.contains("\"module\": \":app\""))
                assertTrue(moduleText.contains("\"composableCount\": 2"))
            } finally {
                client.close()
                server.close()
            }
        }

    private fun toolResultProperties(
        tools: List<io.modelcontextprotocol.kotlin.sdk.types.Tool>,
        toolName: String,
        resultName: String,
    ): kotlinx.serialization.json.JsonObject {
        val tool = assertNotNull(tools.firstOrNull { it.name == toolName })
        val outputSchema = tool.outputSchema ?: error("$toolName outputSchema was missing")
        val outputProperties = assertNotNull(outputSchema.properties)
        return assertNotNull(
            outputProperties[resultName]
                ?.jsonObject
                ?.get("properties")
                ?.jsonObject,
        )
    }

    private fun schemaPropertyType(
        properties: kotlinx.serialization.json.JsonObject,
        name: String,
    ): String? =
        properties[name]
            ?.jsonObject
            ?.get("type")
            ?.jsonPrimitive
            ?.content

    private fun nestedSchemaPropertyType(
        properties: kotlinx.serialization.json.JsonObject,
        name: String,
        nestedName: String,
    ): String? =
        properties[name]
            ?.jsonObject
            ?.get("properties")
            ?.jsonObject
            ?.let { nestedProperties -> schemaPropertyType(nestedProperties, nestedName) }

    private fun arrayItemSchemaType(
        properties: kotlinx.serialization.json.JsonObject,
        name: String,
    ): String? =
        properties[name]
            ?.jsonObject
            ?.get("items")
            ?.jsonObject
            ?.get("type")
            ?.jsonPrimitive
            ?.content

    private fun anyOfSchemaPropertyTypes(
        properties: kotlinx.serialization.json.JsonObject,
        name: String,
    ): List<String> =
        properties[name]
            ?.jsonObject
            ?.get("anyOf")
            ?.jsonArray
            ?.mapNotNull { schema ->
                schema.jsonObject["type"]
                    ?.jsonPrimitive
                    ?.content
            }.orEmpty()

    private fun nestedArrayItemSchemaPropertyType(
        properties: kotlinx.serialization.json.JsonObject,
        name: String,
        nestedName: String,
    ): String? =
        properties[name]
            ?.jsonObject
            ?.get("items")
            ?.jsonObject
            ?.get("properties")
            ?.jsonObject
            ?.let { nestedProperties -> schemaPropertyType(nestedProperties, nestedName) }

    @Test
    fun `malformed tool arguments return deterministic tool error without closing session`() =
        runTest {
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

                val result =
                    client.callTool(
                        "search_composables",
                        mapOf("query" to mapOf("nested" to "value"), "limit" to 1),
                    )

                assertEquals(true, result.isError)
                val text = (result.content.firstOrNull() as? TextContent)?.text
                assertNotNull(text)
                assertTrue(text.contains("Invalid search_composables argument 'query': expected string"))

                val status = client.callTool("index_status", emptyMap())
                assertEquals(null, status.isError)
                val statusText = (status.content.firstOrNull() as? TextContent)?.text
                assertNotNull(statusText)
                assertTrue(statusText.contains("\"exists\""))
            } finally {
                client.close()
                server.close()
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
                    ComposableDeclaration(
                        id = ":app:main:dev.account.AccountSummary#",
                        module = ":app",
                        sourceSet = "main",
                        packageName = "dev.account",
                        name = "AccountSummary",
                        visibility = "public",
                        source = SourceLocation("app/src/main/kotlin/dev/account/AccountSummary.kt", 1, 1),
                        kdoc = null,
                        parameters = emptyList(),
                        annotations = listOf("@Composable"),
                        previews = emptyList(),
                    ),
                ),
        )
}
