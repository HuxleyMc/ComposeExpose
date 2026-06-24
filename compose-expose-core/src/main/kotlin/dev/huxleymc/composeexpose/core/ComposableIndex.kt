package dev.huxleymc.composeexpose.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ComposableIndex(
    val metadata: IndexMetadata,
    val composables: List<ComposableDeclaration>,
)

@Serializable
data class IndexMetadata(
    val generatedAtEpochMillis: Long,
    val projectRoot: String,
    val modules: List<String>,
    val sourceRoots: List<String>,
)

@Serializable
data class ComposableDeclaration(
    val id: String,
    val module: String,
    val sourceSet: String,
    val packageName: String,
    val name: String,
    val visibility: String,
    val source: SourceLocation,
    val kdoc: Kdoc?,
    val parameters: List<ComposableParameter>,
    val annotations: List<String>,
    val previews: List<PreviewDeclaration>,
)

@Serializable
data class SourceLocation(
    val file: String,
    val line: Int,
    val column: Int,
)

@Serializable
data class Kdoc(
    val summary: String,
    val body: String,
)

@Serializable
data class ComposableParameter(
    val name: String,
    val type: String,
    val hasDefault: Boolean,
)

@Serializable
data class PreviewDeclaration(
    val annotation: String,
    val name: String? = null,
    val group: String? = null,
    val arguments: Map<String, String> = emptyMap(),
)

object ComposableIndexJson {
    private val json = Json {
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(index: ComposableIndex): String = json.encodeToString(ComposableIndex.serializer(), index)

    fun decode(value: String): ComposableIndex = json.decodeFromString(ComposableIndex.serializer(), value)
}
