package dev.huxleymc.composeexpose.mcp

import dev.huxleymc.composeexpose.core.ComposableIndexJson
import io.ktor.utils.io.streams.asInput
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Path

fun main(args: Array<String>) {
    try {
        val options = ComposeExposeCli.parse(args)
        val service = ComposeExposeService(projectRoot = options.projectRoot, indexFile = options.indexFile)
        when (options.transport) {
            ServerTransport.Stdio -> runStdioServer(service)
            ServerTransport.Http -> runHttpServer(service, options.port)
        }
    } catch (help: HelpRequested) {
        println(help.message)
    } catch (error: IllegalArgumentException) {
        System.err.println(error.message)
        kotlin.system.exitProcess(2)
    }
}

fun runHttpServer(service: ComposeExposeService, port: Int) {
    val mcpServer = buildComposeExposeMcpServer(service)
    embeddedServer(CIO, host = "127.0.0.1", port = port) {
        mcpStreamableHttp { mcpServer }
    }.start(wait = true)
}

fun runStdioServer(service: ComposeExposeService) {
    val server = buildComposeExposeMcpServer(service)
    val transport = StdioServerTransport(
        input = System.`in`.asInput(),
        output = System.out.asSink().buffered(),
    )
    runBlocking {
        val session = server.createSession(transport)
        val done = Job()
        session.onClose { done.complete() }
        done.join()
    }
}

fun buildComposeExposeMcpServer(service: ComposeExposeService): Server {
    val json = Json { prettyPrint = true }
    val server = Server(
        serverInfo = Implementation(name = "compose-expose", version = "0.1.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
                resources = ServerCapabilities.Resources(subscribe = false, listChanged = false),
            ),
        ),
    )

    server.addResource(
        uri = "compose-expose://index",
        name = "Compose composable index",
        description = "Full generated ComposeExpose index.",
        mimeType = "application/json",
    ) { request ->
        ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    text = ComposableIndexJson.encode(service.loadIndex()),
                    uri = request.uri,
                    mimeType = "application/json",
                ),
            ),
        )
    }

    server.addResource(
        uri = "compose-expose://modules",
        name = "Compose module summary",
        description = "Indexed module names and source roots.",
        mimeType = "application/json",
    ) { request ->
        val status = service.indexStatus()
        ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    text = json.encodeToString(status),
                    uri = request.uri,
                    mimeType = "application/json",
                ),
            ),
        )
    }

    server.addTool(
        name = "search_composables",
        description = "Search indexed Jetpack Compose composables by name, package, or KDoc.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("query", buildJsonObject { put("type", "string") })
                put("module", buildJsonObject { put("type", "string") })
                put("sourceSet", buildJsonObject { put("type", "string") })
            },
        ),
    ) { request ->
        val args = request.arguments
        val result = service.searchComposables(
            query = args?.get("query")?.jsonPrimitive?.contentOrNull,
            module = args?.get("module")?.jsonPrimitive?.contentOrNull,
            sourceSet = args?.get("sourceSet")?.jsonPrimitive?.contentOrNull,
        )
        CallToolResult(content = listOf(TextContent(json.encodeToString(result))))
    }

    server.addTool(
        name = "get_composable",
        description = "Return one indexed composable by stable id.",
        inputSchema = ToolSchema(
            required = listOf("id"),
            properties = buildJsonObject {
                put("id", buildJsonObject { put("type", "string") })
            },
        ),
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
        CallToolResult(content = listOf(TextContent(json.encodeToString(service.getComposable(id)))))
    }

    server.addTool(
        name = "list_previews",
        description = "List indexed Compose previews, optionally filtered by preview group.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("group", buildJsonObject { put("type", "string") })
            },
        ),
    ) { request ->
        val group = request.arguments?.get("group")?.jsonPrimitive?.contentOrNull
        CallToolResult(content = listOf(TextContent(json.encodeToString(service.listPreviews(group)))))
    }

    server.addTool(
        name = "refresh_index",
        description = "Run the Gradle composeExposeIndex task and reload the generated index.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("module", buildJsonObject { put("type", "string") })
            },
        ),
    ) { request ->
        val module = request.arguments?.get("module")?.jsonPrimitive?.contentOrNull
        val result = runBlocking { service.refreshIndex(module) }
        CallToolResult(content = listOf(TextContent(json.encodeToString(result))))
    }

    server.addTool(
        name = "index_status",
        description = "Report index age, source roots, modules, and whether sources are newer than the index.",
    ) {
        CallToolResult(content = listOf(TextContent(json.encodeToString(service.indexStatus()))))
    }

    return server
}
