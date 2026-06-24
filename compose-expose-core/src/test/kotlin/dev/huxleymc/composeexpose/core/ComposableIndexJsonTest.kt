package dev.huxleymc.composeexpose.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposableIndexJsonTest {
    @Test
    fun `serializes stable index schema`() {
        val index =
            ComposableIndex(
                metadata =
                    IndexMetadata(
                        generatedAtEpochMillis = 1234L,
                        projectRoot = "/project",
                        modules = listOf(":app"),
                        sourceRoots = listOf("/project/app/src/main/kotlin"),
                    ),
                composables =
                    listOf(
                        ComposableDeclaration(
                            id = ":app:main:dev.example.AccountCard#title:String",
                            module = ":app",
                            sourceSet = "main",
                            packageName = "dev.example",
                            name = "AccountCard",
                            visibility = "public",
                            source = SourceLocation("app/src/main/kotlin/dev/example/Cards.kt", 12, 1),
                            kdoc = Kdoc("Shows the primary account card.", "Shows the primary account card."),
                            parameters = listOf(ComposableParameter("title", "String", hasDefault = false)),
                            annotations = listOf("@Composable"),
                            previews = emptyList(),
                        ),
                    ),
            )

        val json = ComposableIndexJson.encode(index)
        assertTrue(json.contains("\"generatedAtEpochMillis\": 1234"))
        assertTrue(json.contains("\"composables\""))
        assertEquals(index, ComposableIndexJson.decode(json))
    }
}
