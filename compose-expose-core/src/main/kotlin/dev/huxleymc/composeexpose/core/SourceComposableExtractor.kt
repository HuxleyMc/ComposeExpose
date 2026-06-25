package dev.huxleymc.composeexpose.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo

class SourceComposableExtractor {
    fun extract(
        module: String,
        sourceSet: String,
        sourceRoots: List<Path>,
        projectRoot: Path,
    ): ComposableIndex {
        val composables =
            sourceRoots
                .flatMap { root -> kotlinFiles(root).flatMap { file -> extractFile(module, sourceSet, file, projectRoot) } }
                .sortedWith(compareBy<ComposableDeclaration> { it.source.file }.thenBy { it.source.line }.thenBy { it.name })

        return ComposableIndex(
            metadata =
                IndexMetadata(
                    generatedAtEpochMillis = System.currentTimeMillis(),
                    projectRoot = projectRoot.toAbsolutePath().normalize().toString(),
                    modules = listOf(module),
                    sourceRoots = sourceRoots.map { it.toAbsolutePath().normalize().toString() }.sorted(),
                ),
            composables = composables,
        )
    }

    private fun kotlinFiles(root: Path): List<Path> {
        if (!Files.exists(root)) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.extension == "kt" }
                .sorted()
                .toList()
        }
    }

    private fun extractFile(
        module: String,
        sourceSet: String,
        file: Path,
        projectRoot: Path,
    ): List<ComposableDeclaration> {
        val lines = file.readLines()
        val packageName = lines.firstNotNullOfOrNull { packageRegex.find(it)?.groupValues?.get(1) }.orEmpty()
        val multipreviews = collectMultipreviews(lines)
        val declarations = mutableListOf<ComposableDeclaration>()
        var pendingKdoc: Kdoc? = null
        val pendingAnnotations = mutableListOf<String>()
        var index = 0

        while (index < lines.size) {
            val trimmed = lines[index].trim()
            when {
                trimmed.startsWith("/**") -> {
                    val (kdoc, next) = readKdoc(lines, index)
                    pendingKdoc = kdoc
                    index = next
                    continue
                }

                trimmed.startsWith("@") -> {
                    val inline = readInlineAnnotatedFunction(trimmed)
                    if (inline != null) {
                        val startLine = index + 1
                        val signatureLines = lines.toMutableList()
                        signatureLines[index] = inline.signatureStart
                        val (signature, next) = readFunctionSignature(signatureLines, index)
                        val declaration =
                            parseFunction(
                                module = module,
                                sourceSet = sourceSet,
                                packageName = packageName,
                                file = file,
                                projectRoot = projectRoot,
                                line = startLine,
                                kdoc = pendingKdoc,
                                annotations = pendingAnnotations + inline.annotations,
                                multipreviews = multipreviews,
                                signature = signature,
                            )
                        if (declaration != null) declarations += declaration
                        pendingKdoc = null
                        pendingAnnotations.clear()
                        index = next
                        continue
                    }
                    val (annotation, next) = readAnnotation(lines, index)
                    pendingAnnotations += annotation
                    index = next
                    continue
                }

                trimmed.isBlank() -> {
                    index++
                    continue
                }

                functionStartRegex.containsMatchIn(trimmed) -> {
                    val startLine = index + 1
                    val (signature, next) = readFunctionSignature(lines, index)
                    val declaration =
                        parseFunction(
                            module = module,
                            sourceSet = sourceSet,
                            packageName = packageName,
                            file = file,
                            projectRoot = projectRoot,
                            line = startLine,
                            kdoc = pendingKdoc,
                            annotations = pendingAnnotations.toList(),
                            multipreviews = multipreviews,
                            signature = signature,
                        )
                    if (declaration != null) declarations += declaration
                    pendingKdoc = null
                    pendingAnnotations.clear()
                    index = next
                    continue
                }

                else -> {
                    pendingKdoc = null
                    pendingAnnotations.clear()
                }
            }
            index++
        }
        return declarations
    }

    private fun collectMultipreviews(lines: List<String>): Map<String, List<PreviewDeclaration>> {
        val result = mutableMapOf<String, List<PreviewDeclaration>>()
        val pendingAnnotations = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            when {
                trimmed.startsWith("@") -> {
                    val (annotation, next) = readAnnotation(lines, index)
                    pendingAnnotations += annotation
                    index = next
                    continue
                }

                annotationClassRegex.containsMatchIn(trimmed) -> {
                    val name = annotationClassRegex.find(trimmed)?.groupValues?.get(1)
                    val previews = pendingAnnotations.flatMap { parsePreviewAnnotation(it) }
                    if (name != null && previews.isNotEmpty()) result[name] = previews
                    pendingAnnotations.clear()
                }

                trimmed.isNotBlank() -> {
                    pendingAnnotations.clear()
                }
            }
            index++
        }
        return result
    }

    private fun readKdoc(
        lines: List<String>,
        start: Int,
    ): Pair<Kdoc, Int> {
        val raw = mutableListOf<String>()
        var index = start
        while (index < lines.size) {
            raw += lines[index]
            if (lines[index].contains("*/")) break
            index++
        }
        val bodyLines =
            raw
                .map { line ->
                    line
                        .trim()
                        .removePrefix("/**")
                        .removeSuffix("*/")
                        .trim()
                        .removePrefix("*")
                        .trim()
                }.filter { it.isNotBlank() }
        val body = bodyLines.joinToString("\n")
        return Kdoc(summary = bodyLines.firstOrNull().orEmpty(), body = body) to index + 1
    }

    private fun readAnnotation(
        lines: List<String>,
        start: Int,
    ): Pair<String, Int> {
        val parts = mutableListOf<String>()
        var index = start
        var balance = 0
        do {
            val line = lines[index].trim()
            parts += line
            balance += line.count { it == '(' }
            balance -= line.count { it == ')' }
            index++
        } while (index < lines.size && balance > 0)
        return parts.joinToString(" ").replace(Regex("\\s+"), " ") to index
    }

    private fun readInlineAnnotatedFunction(line: String): InlineAnnotatedFunction? {
        val annotations = mutableListOf<String>()
        var cursor = 0
        while (cursor < line.length && line[cursor] == '@') {
            val start = cursor
            var parenBalance = 0
            do {
                val char = line[cursor]
                when (char) {
                    '(' -> parenBalance++
                    ')' -> if (parenBalance > 0) parenBalance--
                }
                cursor++
            } while (cursor < line.length && (parenBalance > 0 || !line[cursor].isWhitespace()))
            annotations += line.substring(start, cursor).trim()
            while (cursor < line.length && line[cursor].isWhitespace()) cursor++
        }
        val signatureStart = line.substring(cursor).trim()
        if (annotations.isEmpty() || !functionStartRegex.containsMatchIn(signatureStart)) return null
        return InlineAnnotatedFunction(annotations = annotations, signatureStart = signatureStart)
    }

    private fun readFunctionSignature(
        lines: List<String>,
        start: Int,
    ): Pair<String, Int> {
        val parts = mutableListOf<String>()
        var index = start
        var parenBalance = 0
        do {
            val line = lines[index]
            parts += line.trim()
            parenBalance += line.count { it == '(' }
            parenBalance -= line.count { it == ')' }
            index++
        } while (index < lines.size && (parenBalance > 0 || !lineEndsSignature(parts.last())))
        return parts.joinToString(" ").substringBefore("{").trim() to index
    }

    private fun lineEndsSignature(line: String): Boolean = line.contains(")") || line.contains("{") || line.contains("=")

    private fun parseFunction(
        module: String,
        sourceSet: String,
        packageName: String,
        file: Path,
        projectRoot: Path,
        line: Int,
        kdoc: Kdoc?,
        annotations: List<String>,
        multipreviews: Map<String, List<PreviewDeclaration>>,
        signature: String,
    ): ComposableDeclaration? {
        if (annotations.none { annotationName(it) == "Composable" }) return null
        val match = functionRegex.find(signature) ?: return null
        val visibility = match.groups["visibility"]?.value ?: "public"
        val name = match.groups["name"]?.value ?: return null
        val parameterText = match.groups["params"]?.value.orEmpty()
        val parameters = parseParameters(parameterText)
        val previews =
            annotations.flatMap { annotation ->
                parsePreviewAnnotation(annotation).ifEmpty {
                    multipreviews[annotationName(annotation)].orEmpty()
                }
            }
        val id = stableId(module, sourceSet, packageName, name, parameters)
        val relativeFile =
            file
                .toAbsolutePath()
                .normalize()
                .relativeTo(projectRoot.toAbsolutePath().normalize())
                .toString()
        return ComposableDeclaration(
            id = id,
            module = module,
            sourceSet = sourceSet,
            packageName = packageName,
            name = name,
            visibility = visibility,
            source = SourceLocation(relativeFile, line, 1),
            kdoc = kdoc,
            parameters = parameters,
            annotations = annotations.map { "@${annotationName(it)}" },
            previews = previews,
        )
    }

    private fun parseParameters(parameterText: String): List<ComposableParameter> {
        return splitTopLevel(parameterText)
            .mapNotNull { raw ->
                val cleaned = raw.trim().trimEnd(',')
                if (cleaned.isBlank()) return@mapNotNull null
                val withoutAnnotations = cleaned.replace(Regex("@\\w+(\\([^)]*\\))?\\s*"), "")
                val name = withoutAnnotations.substringBefore(":").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val typeAndDefault = withoutAnnotations.substringAfter(":", missingDelimiterValue = "").trim()
                if (typeAndDefault.isBlank()) return@mapNotNull null
                val hasDefault = typeAndDefault.contains("=")
                val type = typeAndDefault.substringBefore("=").trim()
                ComposableParameter(name = name, type = type, hasDefault = hasDefault)
            }
    }

    private fun splitTopLevel(value: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var angle = 0
        var paren = 0
        value.forEach { char ->
            when (char) {
                '<' -> {
                    angle++
                }

                '>' -> {
                    if (angle > 0) angle--
                }

                '(' -> {
                    paren++
                }

                ')' -> {
                    if (paren > 0) paren--
                }

                ',' -> {
                    if (angle == 0 && paren == 0) {
                        result += current.toString()
                        current.clear()
                        return@forEach
                    }
                }
            }
            current.append(char)
        }
        result += current.toString()
        return result
    }

    private fun parsePreviewAnnotation(annotation: String): List<PreviewDeclaration> {
        if (annotationName(annotation) != "Preview") return emptyList()
        val args = parseAnnotationArguments(annotation)
        return listOf(
            PreviewDeclaration(
                annotation = "Preview",
                name = args["name"],
                group = args["group"],
                arguments = args,
            ),
        )
    }

    private fun parseAnnotationArguments(annotation: String): Map<String, String> {
        val args = annotation.substringAfter("(", missingDelimiterValue = "").substringBeforeLast(")")
        if (args.isBlank()) return emptyMap()
        return splitTopLevel(args)
            .mapNotNull { entry ->
                val key = entry.substringBefore("=", missingDelimiterValue = "").trim()
                val value = entry.substringAfter("=", missingDelimiterValue = "").trim().trim('"')
                if (key.isBlank() || value.isBlank()) null else key to value
            }.toMap()
    }

    private fun annotationName(annotation: String): String =
        annotation
            .removePrefix("@")
            .substringBefore("(")
            .substringAfterLast(".")
            .trim()

    private fun stableId(
        module: String,
        sourceSet: String,
        packageName: String,
        name: String,
        parameters: List<ComposableParameter>,
    ): String {
        val qualifiedName = if (packageName.isBlank()) name else "$packageName.$name"
        val parameterSignature = parameters.joinToString(",") { "${it.name}:${it.type}" }
        return "$module:$sourceSet:$qualifiedName#$parameterSignature"
    }

    private data class InlineAnnotatedFunction(
        val annotations: List<String>,
        val signatureStart: String,
    )

    private companion object {
        val packageRegex = Regex("^\\s*package\\s+([A-Za-z0-9_.]+)")
        val annotationClassRegex = Regex("\\bannotation\\s+class\\s+(\\w+)")
        const val TYPE_PARAMETERS_PATTERN = "<(?:[^<>]|<[^<>]*>)+>"
        val functionStartRegex = Regex("\\bfun\\s+(?:$TYPE_PARAMETERS_PATTERN\\s+)?(?:[\\w.<>?]+\\.)?\\w+\\s*\\(")
        val functionRegex =
            Regex(
                "(?:(?<visibility>public|private|internal|protected)\\s+)?(?:[\\w<>]+\\s+)*fun\\s+(?:$TYPE_PARAMETERS_PATTERN\\s+)?(?:[\\w.<>?]+\\.)?(?<name>\\w+)\\s*\\((?<params>.*)\\)",
            )
    }
}
