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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
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
            instructions = COMPOSE_EXPOSE_MCP_INSTRUCTIONS,
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

    server.addResourceTemplate(
        uriTemplate = "compose-expose://module/{module}",
        name = "Compose module detail",
        description = "Summary for one indexed Gradle module.",
        mimeType = "application/json",
    ) { request, variables ->
        val module = variables["module"].orEmpty()
        ReadResourceResult(
            contents =
                listOf(
                    TextResourceContents(
                        text = json.encodeToString(service.moduleSummary(module)),
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
                        put(
                            "query",
                            stringSchema("Text to match against composable name, package, KDoc, parameters, annotations, or previews."),
                        )
                        put("module", stringSchema("Optional Gradle module path filter, for example :app."))
                        put("sourceSet", stringSchema("Optional source set filter, for example main, debug, free, or paid."))
                        put("limit", integerSchema("Maximum results to return.", minimum = 1, maximum = 100))
                    },
            ),
        outputSchema = toolOutputSchema("results", arraySchema("Matched composable declarations.", composableSchema())),
    ) { request ->
        toolResult(json, "results") {
            val args = request.arguments
            service.searchComposables(
                query = args.optionalString("search_composables", "query"),
                module = args.optionalString("search_composables", "module"),
                sourceSet = args.optionalString("search_composables", "sourceSet"),
                limit = args.optionalInt("search_composables", "limit") ?: 20,
            )
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
                        put("id", stringSchema("Stable composable id from search_composables or the index resource."))
                    },
            ),
        outputSchema =
            toolOutputSchema(
                "composable",
                nullableSchema(
                    "Matched composable declaration, or null when the id is unknown.",
                    composableSchema(),
                ),
            ),
    ) { request ->
        toolResult(json, "composable") {
            val id = request.arguments.requiredString("get_composable", "id")
            service.getComposable(id)
        }
    }

    server.addTool(
        name = "list_previews",
        description = "List indexed Compose previews, optionally filtered by preview group.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        put("group", stringSchema("Optional Compose preview group filter."))
                    },
            ),
        outputSchema =
            toolOutputSchema(
                "previews",
                arraySchema(
                    "Indexed Compose preview declarations with their parent composable ids.",
                    previewSearchResultSchema(),
                ),
            ),
    ) { request ->
        toolResult(json, "previews") {
            val group = request.arguments.optionalString("list_previews", "group")
            service.listPreviews(group)
        }
    }

    server.addTool(
        name = "refresh_index",
        description = "Run the Gradle composeExposeIndex task and reload the generated index.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        put("module", stringSchema("Optional Gradle module path to index before refreshing the aggregate index."))
                    },
            ),
        outputSchema =
            toolOutputSchema(
                "result",
                refreshResultSchema(),
            ),
    ) { request ->
        toolResult(json, "result") {
            val module = request.arguments.optionalString("refresh_index", "module")
            runBlocking { service.refreshIndex(module) }
        }
    }

    server.addTool(
        name = "index_status",
        description = "Report index age, source roots, modules, and whether sources are newer than the index.",
        outputSchema = toolOutputSchema("status", indexStatusSchema()),
    ) {
        toolResult(json, "status") {
            service.indexStatus()
        }
    }

    return server
}

private fun toolOutputSchema(
    key: String,
    schema: JsonObject,
): ToolSchema =
    ToolSchema(
        required = listOf(key),
        properties =
            buildJsonObject {
                put(key, schema)
            },
    )

private fun stringSchema(description: String): JsonObject =
    buildJsonObject {
        put("type", "string")
        put("description", description)
    }

private fun integerSchema(
    description: String,
    minimum: Int,
    maximum: Int,
): JsonObject =
    buildJsonObject {
        put("type", "integer")
        put("description", description)
        put("minimum", minimum)
        put("maximum", maximum)
    }

private fun longSchema(description: String): JsonObject =
    buildJsonObject {
        put("type", "integer")
        put("description", description)
        put("format", "int64")
    }

private fun arraySchema(
    description: String,
    itemSchema: JsonObject = objectSchema("Array item."),
): JsonObject =
    buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", itemSchema)
    }

private fun objectSchema(
    description: String,
    required: List<String> = emptyList(),
    properties: JsonObject = buildJsonObject {},
): JsonObject =
    buildJsonObject {
        put("type", "object")
        put("description", description)
        put("properties", properties)
        if (required.isNotEmpty()) {
            put(
                "required",
                buildJsonArray {
                    required.forEach { add(JsonPrimitive(it)) }
                },
            )
        }
    }

private fun booleanSchema(description: String): JsonObject =
    buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

private fun nullableStringSchema(description: String): JsonObject = nullableSchema(description, stringSchema(description))

private fun nullableLongSchema(description: String): JsonObject = nullableSchema(description, longSchema(description))

private fun nullableSchema(
    description: String,
    schema: JsonObject,
): JsonObject =
    buildJsonObject {
        put("description", description)
        put(
            "anyOf",
            buildJsonArray {
                add(schema)
                add(
                    buildJsonObject {
                        put("type", "null")
                    },
                )
            },
        )
    }

private fun stringArraySchema(description: String): JsonObject = arraySchema(description, stringSchema("String value."))

private fun stringMapSchema(description: String): JsonObject =
    objectSchema(description).let { baseSchema ->
        buildJsonObject {
            baseSchema.forEach { (key, value) -> put(key, value) }
            put("additionalProperties", stringSchema("Argument value."))
        }
    }

private fun sourceLocationSchema(): JsonObject =
    objectSchema(
        description = "Source file location for the composable declaration.",
        required = listOf("file", "line", "column"),
        properties =
            buildJsonObject {
                put("file", stringSchema("Project-relative or absolute source file path."))
                put("line", integerSchema("One-based source line.", minimum = 1, maximum = Int.MAX_VALUE))
                put("column", integerSchema("One-based source column.", minimum = 1, maximum = Int.MAX_VALUE))
            },
    )

private fun kdocSchema(): JsonObject =
    objectSchema(
        description = "Parsed KDoc summary and body.",
        required = listOf("summary", "body"),
        properties =
            buildJsonObject {
                put("summary", stringSchema("First KDoc sentence or paragraph."))
                put("body", stringSchema("Full KDoc body text."))
            },
    )

private fun parameterSchema(): JsonObject =
    objectSchema(
        description = "Composable parameter declaration.",
        required = listOf("name", "type", "hasDefault"),
        properties =
            buildJsonObject {
                put("name", stringSchema("Parameter name."))
                put("type", stringSchema("Kotlin parameter type text."))
                put("hasDefault", booleanSchema("Whether the parameter declares a default value."))
            },
    )

private fun previewSchema(): JsonObject =
    objectSchema(
        description = "Compose Preview or multipreview annotation metadata.",
        required = listOf("annotation", "arguments"),
        properties =
            buildJsonObject {
                put("annotation", stringSchema("Preview annotation simple or qualified name."))
                put("name", nullableStringSchema("Preview display name, when present."))
                put("group", nullableStringSchema("Preview group, when present."))
                put("arguments", stringMapSchema("Preview annotation argument values."))
            },
    )

private fun composableSchema(): JsonObject =
    objectSchema(
        description = "Indexed Jetpack Compose composable declaration.",
        required =
            listOf(
                "id",
                "module",
                "sourceSet",
                "packageName",
                "name",
                "visibility",
                "source",
                "parameters",
                "annotations",
                "previews",
            ),
        properties =
            buildJsonObject {
                put("id", stringSchema("Stable composable id."))
                put("module", stringSchema("Gradle module path."))
                put("sourceSet", stringSchema("Android or Kotlin source set."))
                put("packageName", stringSchema("Kotlin package name."))
                put("name", stringSchema("Composable function name."))
                put("visibility", stringSchema("Kotlin visibility."))
                put("source", sourceLocationSchema())
                put("kdoc", nullableSchema("KDoc metadata, or null when absent.", kdocSchema()))
                put("parameters", arraySchema("Composable parameters.", parameterSchema()))
                put("annotations", stringArraySchema("Function annotations."))
                put("previews", arraySchema("Preview annotations attached to the composable.", previewSchema()))
            },
    )

private fun previewSearchResultSchema(): JsonObject =
    objectSchema(
        description = "Preview search result with parent composable metadata.",
        required = listOf("composableId", "composableName", "preview"),
        properties =
            buildJsonObject {
                put("composableId", stringSchema("Stable composable id that owns the preview."))
                put("composableName", stringSchema("Composable function name that owns the preview."))
                put("preview", previewSchema())
            },
    )

private fun indexStatusSchema(): JsonObject =
    objectSchema(
        description = "Current index freshness and source-root status.",
        required =
            listOf(
                "exists",
                "isStale",
                "generatedAtEpochMillis",
                "modules",
                "sourceRoots",
                "newerSources",
                "refreshInProgress",
            ),
        properties =
            buildJsonObject {
                put("exists", booleanSchema("Whether the aggregate index file exists."))
                put("isStale", booleanSchema("Whether sources are newer than the index or the index cannot be read."))
                put("generatedAtEpochMillis", nullableLongSchema("Index generation timestamp, or null when unavailable."))
                put("modules", stringArraySchema("Indexed Gradle module paths."))
                put("sourceRoots", stringArraySchema("Source roots scanned for staleness checks."))
                put("newerSources", stringArraySchema("Known Kotlin source files newer than the index."))
                put("refreshInProgress", booleanSchema("Whether refresh_index is currently running Gradle."))
                put("error", nullableStringSchema("Recoverable index read or status error, when present."))
            },
    )

private fun refreshResultSchema(): JsonObject =
    objectSchema(
        description = "Refresh result with success, Gradle output, and a fresh index_status snapshot.",
        required = listOf("success", "output", "status"),
        properties =
            buildJsonObject {
                put("success", booleanSchema("Whether Gradle refresh completed successfully."))
                put("output", stringSchema("Gradle output or recoverable failure message."))
                put("status", indexStatusSchema())
            },
    )

private const val COMPOSE_EXPOSE_MCP_INSTRUCTIONS =
    """
Use ComposeExpose to discover reusable Jetpack Compose declarations from the generated index before grepping source.
Call index_status first when freshness matters; if exists is false, isStale is true, or error is set, call refresh_index and retry discovery after it returns.
Prefer search_composables with a focused query and small limit, then get_composable for full metadata by stable id.
Use structuredContent from tool results when your client supports it; text content is JSON kept for compatibility.
Use compose-expose://modules or compose-expose://module/{module} for compact module context, and compose-expose://index only when the full index is needed.
"""

private inline fun <reified T> toolResult(
    json: Json,
    structuredKey: String,
    block: () -> T,
): CallToolResult =
    try {
        val result = block()
        CallToolResult(
            content = listOf(TextContent(json.encodeToString(result))),
            structuredContent =
                buildJsonObject {
                    put(structuredKey, json.encodeToJsonElement(result))
                },
        )
    } catch (error: ToolArgumentException) {
        val message = error.message.orEmpty()
        CallToolResult(
            content = listOf(TextContent(message)),
            isError = true,
            structuredContent =
                buildJsonObject {
                    put("error", message)
                },
        )
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
