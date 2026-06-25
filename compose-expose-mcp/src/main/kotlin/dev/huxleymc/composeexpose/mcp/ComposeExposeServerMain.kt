package dev.huxleymc.composeexpose.mcp

import dev.huxleymc.composeexpose.core.ComposableIndexJson
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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

fun runHttpServer(
    service: ComposeExposeService,
    port: Int,
) {
    embeddedServer(CIO, host = "127.0.0.1", port = port) {
        installComposeExposeHttpTransport(service)
    }.start(wait = true)
}

fun Application.installComposeExposeHttpTransport(service: ComposeExposeService) {
    mcpStatelessStreamableHttp {
        buildComposeExposeMcpServer(service)
    }
}

fun runStdioServer(service: ComposeExposeService) {
    val server = buildComposeExposeMcpServer(service)
    val transport =
        StdioServerTransport(
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
    val server =
        Server(
            serverInfo = Implementation(name = "compose-expose", version = resolveComposeExposeServerVersion()),
            options =
                ServerOptions(
                    capabilities =
                        ServerCapabilities(
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
            contents =
                listOf(
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
        description = "Compact per-module counts, source sets, packages, and source roots.",
        mimeType = "application/json",
    ) { request ->
        ReadResourceResult(
            contents =
                listOf(
                    TextResourceContents(
                        text = json.encodeToString(service.moduleSummaries()),
                        uri = request.uri,
                        mimeType = "application/json",
                    ),
                ),
        )
    }

    server.addTool(
        name = "search_composables",
        description = "Search indexed Jetpack Compose composables by name, package, or KDoc.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        put("query", buildJsonObject { put("type", "string") })
                        put("module", buildJsonObject { put("type", "string") })
                        put("sourceSet", buildJsonObject { put("type", "string") })
                        put("limit", buildJsonObject { put("type", "integer") })
                    },
            ),
    ) { request ->
        toolResult {
            val args = request.arguments
            val result =
                service.searchComposables(
                    query = args.optionalString("search_composables", "query"),
                    module = args.optionalString("search_composables", "module"),
                    sourceSet = args.optionalString("search_composables", "sourceSet"),
                    limit = args.optionalInt("search_composables", "limit") ?: 20,
                )
            json.encodeToString(result)
        }
    }

    server.addTool(
        name = "get_composable",
        description = "Return one indexed composable by stable id.",
        inputSchema =
            ToolSchema(
                required = listOf("id"),
                properties =
                    buildJsonObject {
                        put("id", buildJsonObject { put("type", "string") })
                    },
            ),
    ) { request ->
        toolResult {
            val id = request.arguments.requiredString("get_composable", "id")
            json.encodeToString(service.getComposable(id))
        }
    }

    server.addTool(
        name = "list_previews",
        description = "List indexed Compose previews, optionally filtered by preview group.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        put("group", buildJsonObject { put("type", "string") })
                    },
            ),
    ) { request ->
        toolResult {
            val group = request.arguments.optionalString("list_previews", "group")
            json.encodeToString(service.listPreviews(group))
        }
    }

    server.addTool(
        name = "refresh_index",
        description = "Run the Gradle composeExposeIndex task and reload the generated index.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        put("module", buildJsonObject { put("type", "string") })
                    },
            ),
    ) { request ->
        toolResult {
            val module = request.arguments.optionalString("refresh_index", "module")
            val result = runBlocking { service.refreshIndex(module) }
            json.encodeToString(result)
        }
    }

    server.addTool(
        name = "index_status",
        description = "Report index age, source roots, modules, and whether sources are newer than the index.",
    ) {
        CallToolResult(content = listOf(TextContent(json.encodeToString(service.indexStatus()))))
    }

    return server
}

private fun toolResult(block: () -> String): CallToolResult =
    try {
        CallToolResult(content = listOf(TextContent(block())))
    } catch (error: ToolArgumentException) {
        CallToolResult(content = listOf(TextContent(error.message.orEmpty())), isError = true)
    }

private fun JsonObject?.optionalString(
    toolName: String,
    argumentName: String,
): String? {
    val value = this?.get(argumentName) ?: return null
    val primitive =
        value as? JsonPrimitive
            ?: throw ToolArgumentException("Invalid $toolName argument '$argumentName': expected string")
    if (!primitive.isString && primitive.contentOrNull != null) {
        throw ToolArgumentException("Invalid $toolName argument '$argumentName': expected string")
    }
    return primitive.contentOrNull
}

private fun JsonObject?.requiredString(
    toolName: String,
    argumentName: String,
): String =
    optionalString(toolName, argumentName)
        ?: throw ToolArgumentException("Invalid $toolName argument '$argumentName': expected string")

private fun JsonObject?.optionalInt(
    toolName: String,
    argumentName: String,
): Int? {
    val value = this?.get(argumentName) ?: return null
    val primitive =
        value as? JsonPrimitive
            ?: throw ToolArgumentException("Invalid $toolName argument '$argumentName': expected integer")
    if (primitive.isString) {
        throw ToolArgumentException("Invalid $toolName argument '$argumentName': expected integer")
    }
    return primitive.intOrNull
        ?: throw ToolArgumentException("Invalid $toolName argument '$argumentName': expected integer")
}

private class ToolArgumentException(
    message: String,
) : IllegalArgumentException(message)
