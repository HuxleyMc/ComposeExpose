package dev.huxleymc.composeexpose.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import dev.huxleymc.composeexpose.core.ComposableDeclaration
import dev.huxleymc.composeexpose.core.ComposableIndex
import dev.huxleymc.composeexpose.core.ComposableIndexJson
import dev.huxleymc.composeexpose.core.ComposableParameter
import dev.huxleymc.composeexpose.core.IndexMetadata
import dev.huxleymc.composeexpose.core.Kdoc
import dev.huxleymc.composeexpose.core.PreviewDeclaration
import dev.huxleymc.composeexpose.core.SourceLocation
import dev.huxleymc.composeexpose.core.SourceSetDetector

class ComposeExposeSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ComposeExposeSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options,
        )
}

class ComposeExposeSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    private var written = false

    override fun process(resolver: Resolver): List<KSFunctionDeclaration> {
        val symbols =
            resolver
                .getSymbolsWithAnnotation("androidx.compose.runtime.Composable")
                .filterIsInstance<KSFunctionDeclaration>()
                .toList()

        val deferred = symbols.filterNot { it.validate() }
        if (written) return deferred

        val module = options["composeExpose.module"] ?: ":unknown"
        val fallbackSourceSet = options["composeExpose.sourceSet"] ?: "main"
        val projectRoot = options["composeExpose.projectRoot"].orEmpty()
        val declarations =
            symbols
                .filter { it.validate() }
                .map { it.toComposableDeclaration(module, fallbackSourceSet, projectRoot) }
                .sortedWith(compareBy<ComposableDeclaration> { it.source.file }.thenBy { it.source.line }.thenBy { it.name })

        val index =
            ComposableIndex(
                metadata =
                    IndexMetadata(
                        generatedAtEpochMillis = System.currentTimeMillis(),
                        projectRoot = projectRoot,
                        modules = listOf(module),
                        sourceRoots = options["composeExpose.sourceRoots"]?.split(";")?.filter { it.isNotBlank() }.orEmpty(),
                    ),
                composables = declarations,
            )

        codeGenerator
            .createNewFileByPath(Dependencies(aggregating = true), "composeExpose/composables", "json")
            .bufferedWriter()
            .use { it.write(ComposableIndexJson.encode(index)) }
        logger.info("ComposeExpose indexed ${declarations.size} composables for $module")
        written = true
        return deferred
    }

    private fun KSFunctionDeclaration.toComposableDeclaration(
        module: String,
        fallbackSourceSet: String,
        projectRoot: String,
    ): ComposableDeclaration {
        val location = location as? FileLocation
        val filePath = location?.filePath.orEmpty()
        val sourceSet = SourceSetDetector.detect(filePath) ?: fallbackSourceSet
        val relativePath =
            if (projectRoot.isNotBlank() && filePath.startsWith(projectRoot)) {
                filePath.removePrefix(projectRoot).trimStart('/')
            } else {
                filePath
            }
        val parameters = parameters.map { it.toComposableParameter() }
        val packageName = packageName.asString()
        val name = simpleName.asString()
        return ComposableDeclaration(
            id = stableId(module, sourceSet, packageName, name, parameters),
            module = module,
            sourceSet = sourceSet,
            packageName = packageName,
            name = name,
            visibility = visibilityName(),
            source = SourceLocation(relativePath, location?.lineNumber ?: 1, 1),
            kdoc =
                docString?.trim()?.let { body ->
                    Kdoc(
                        summary =
                            body
                                .lineSequence()
                                .firstOrNull { it.isNotBlank() }
                                ?.trim()
                                .orEmpty(),
                        body = body,
                    )
                },
            parameters = parameters,
            annotations = annotations.map { "@${it.shortName.asString()}" }.toList(),
            previews = annotations.flatMap { it.toPreviews() }.toList(),
        )
    }

    private fun KSValueParameter.toComposableParameter(): ComposableParameter =
        ComposableParameter(
            name = name?.asString().orEmpty(),
            type =
                type
                    .resolve()
                    .declaration.qualifiedName
                    ?.asString()
                    ?: type
                        .resolve()
                        .declaration.simpleName
                        .asString(),
            hasDefault = hasDefault,
        )

    private fun KSFunctionDeclaration.visibilityName(): String =
        when {
            Modifier.PRIVATE in modifiers -> "private"
            Modifier.INTERNAL in modifiers -> "internal"
            Modifier.PROTECTED in modifiers -> "protected"
            else -> "public"
        }

    private fun KSAnnotation.toPreviews(): List<PreviewDeclaration> {
        val directName = shortName.asString()
        if (directName == "Preview") return listOf(toPreviewDeclaration("Preview"))
        val nested =
            annotationType
                .resolve()
                .declaration.annotations
                .filter { it.shortName.asString() == "Preview" }
        return nested.map { it.toPreviewDeclaration(directName) }.toList()
    }

    private fun KSAnnotation.toPreviewDeclaration(annotation: String): PreviewDeclaration {
        val args =
            arguments
                .mapNotNull { argument ->
                    val name = argument.name?.asString() ?: return@mapNotNull null
                    val value = argument.value?.toString() ?: return@mapNotNull null
                    name to value
                }.toMap()
        return PreviewDeclaration(
            annotation = annotation,
            name = args["name"],
            group = args["group"],
            arguments = args,
        )
    }

    private fun stableId(
        module: String,
        sourceSet: String,
        packageName: String,
        name: String,
        parameters: List<ComposableParameter>,
    ): String {
        val parameterSignature = parameters.joinToString(",") { "${it.name}:${it.type}" }
        return "$module:$sourceSet:$packageName.$name#$parameterSignature"
    }
}
