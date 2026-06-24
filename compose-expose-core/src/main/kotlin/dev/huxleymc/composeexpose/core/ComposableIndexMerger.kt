package dev.huxleymc.composeexpose.core

object ComposableIndexMerger {
    fun merge(projectRoot: String, indexes: List<ComposableIndex>): ComposableIndex {
        val composables = indexes
            .flatMap { it.composables }
            .distinctBy { it.id }
            .sortedWith(compareBy<ComposableDeclaration> { it.module }.thenBy { it.packageName }.thenBy { it.name })

        return ComposableIndex(
            metadata = IndexMetadata(
                generatedAtEpochMillis = System.currentTimeMillis(),
                projectRoot = projectRoot,
                modules = indexes.flatMap { it.metadata.modules }.distinct().sorted(),
                sourceRoots = indexes.flatMap { it.metadata.sourceRoots }.distinct().sorted(),
            ),
            composables = composables,
        )
    }
}
