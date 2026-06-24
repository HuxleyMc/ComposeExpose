package dev.huxleymc.composeexpose.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposableIndexMergerTest {
    @Test
    fun `merge combines module indexes deterministically`() {
        val app = index(":app", "dev.example.AppCard", "AppCard")
        val design = index(":design", "dev.example.DesignButton", "DesignButton")

        val merged = ComposableIndexMerger.merge("/repo", listOf(design, app))

        assertEquals(listOf(":app", ":design"), merged.metadata.modules)
        assertEquals(listOf("AppCard", "DesignButton"), merged.composables.map { it.name })
        assertEquals(listOf("/repo/app/src/main/kotlin", "/repo/design/src/main/kotlin"), merged.metadata.sourceRoots)
    }

    private fun index(module: String, qualifiedName: String, name: String): ComposableIndex {
        return ComposableIndex(
            metadata = IndexMetadata(
                generatedAtEpochMillis = 1L,
                projectRoot = "/repo",
                modules = listOf(module),
                sourceRoots = listOf("/repo/${module.removePrefix(":")}/src/main/kotlin"),
            ),
            composables = listOf(
                ComposableDeclaration(
                    id = "$module:main:$qualifiedName#",
                    module = module,
                    sourceSet = "main",
                    packageName = qualifiedName.substringBeforeLast("."),
                    name = name,
                    visibility = "public",
                    source = SourceLocation("${module.removePrefix(":")}/src/main/kotlin/${name}.kt", 1, 1),
                    kdoc = null,
                    parameters = emptyList(),
                    annotations = listOf("@Composable"),
                    previews = emptyList(),
                ),
            ),
        )
    }
}
