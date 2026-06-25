package dev.huxleymc.composeexpose.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SourceComposableExtractorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracts declaration metadata for composables and previews`() {
        val sourceRoot = tempDir.resolve("src/main/kotlin").createDirectories()
        val source = sourceRoot.resolve("dev/example/Cards.kt")
        source.parent.createDirectories()
        source.writeText(
            """
            package dev.example

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview(name = "Phone", group = "device", widthDp = 390, heightDp = 844)
            @Preview(name = "Tablet", group = "device", widthDp = 840, heightDp = 1180)
            annotation class DevicePreviews

            /**
             * Shows the primary account card.
             *
             * Supports compact and expanded states.
             */
            @DevicePreviews
            @Composable
            internal fun AccountCard(
                title: String,
                balance: Long = 0L,
                onClick: () -> Unit,
            ) {
            }

            @Composable
            private fun NoDocChip(selected: Boolean) {}
            """.trimIndent(),
        )

        val index =
            SourceComposableExtractor().extract(
                module = ":app",
                sourceSet = "main",
                sourceRoots = listOf(sourceRoot),
                projectRoot = tempDir,
            )

        assertEquals(2, index.composables.size)
        val card = assertNotNull(index.composables.singleOrNull { it.name == "AccountCard" })
        assertEquals("dev.example", card.packageName)
        assertEquals("internal", card.visibility)
        assertEquals("Shows the primary account card.", card.kdoc?.summary)
        assertTrue(
            card.kdoc
                ?.body
                .orEmpty()
                .contains("compact and expanded"),
        )
        assertEquals(listOf("title", "balance", "onClick"), card.parameters.map { it.name })
        assertEquals("Long", card.parameters.single { it.name == "balance" }.type)
        assertTrue(card.parameters.single { it.name == "balance" }.hasDefault)
        assertEquals(2, card.previews.size)
        assertEquals("Phone", card.previews.first().name)
        assertEquals("device", card.previews.first().group)
        assertTrue(card.source.file.endsWith("src/main/kotlin/dev/example/Cards.kt"))

        val chip = assertNotNull(index.composables.singleOrNull { it.name == "NoDocChip" })
        assertEquals("private", chip.visibility)
        assertEquals(null, chip.kdoc)
        assertEquals("Boolean", chip.parameters.single().type)
    }

    @Test
    fun `extracts extension composables`() {
        val sourceRoot = tempDir.resolve("src/main/kotlin").createDirectories()
        val source = sourceRoot.resolve("dev/example/Actions.kt")
        source.parent.createDirectories()
        source.writeText(
            """
            package dev.example

            import androidx.compose.foundation.layout.RowScope
            import androidx.compose.runtime.Composable

            @Composable
            fun RowScope.PrimaryAction(
                label: String,
                enabled: Boolean = true,
            ) {
            }
            """.trimIndent(),
        )

        val index =
            SourceComposableExtractor().extract(
                module = ":design",
                sourceSet = "main",
                sourceRoots = listOf(sourceRoot),
                projectRoot = tempDir,
            )

        val action = assertNotNull(index.composables.singleOrNull())
        assertEquals("PrimaryAction", action.name)
        assertEquals("dev.example", action.packageName)
        assertEquals(listOf("label", "enabled"), action.parameters.map { it.name })
        assertEquals(":design:main:dev.example.PrimaryAction#label:String,enabled:Boolean", action.id)
    }

    @Test
    fun `extracts generic composables`() {
        val sourceRoot = tempDir.resolve("src/main/kotlin").createDirectories()
        val source = sourceRoot.resolve("dev/example/Lists.kt")
        source.parent.createDirectories()
        source.writeText(
            """
            package dev.example

            import androidx.compose.runtime.Composable

            @Composable
            fun <T : Any> ReusableList(
                items: List<T>,
                selected: T? = null,
            ) {
            }
            """.trimIndent(),
        )

        val index =
            SourceComposableExtractor().extract(
                module = ":feature",
                sourceSet = "main",
                sourceRoots = listOf(sourceRoot),
                projectRoot = tempDir,
            )

        val list = assertNotNull(index.composables.singleOrNull())
        assertEquals("ReusableList", list.name)
        assertEquals(listOf("items", "selected"), list.parameters.map { it.name })
        assertEquals("List<T>", list.parameters.single { it.name == "items" }.type)
        assertEquals("T?", list.parameters.single { it.name == "selected" }.type)
        assertEquals(":feature:main:dev.example.ReusableList#items:List<T>,selected:T?", list.id)
    }

    @Test
    fun `extracts generic composables with nested type parameter bounds`() {
        val sourceRoot = tempDir.resolve("src/main/kotlin").createDirectories()
        val source = sourceRoot.resolve("dev/example/Ranked.kt")
        source.parent.createDirectories()
        source.writeText(
            """
            package dev.example

            import androidx.compose.runtime.Composable

            @Composable
            fun <T : Comparable<T>> RankedList(
                items: List<T>,
                selected: T,
            ) {
            }
            """.trimIndent(),
        )

        val index =
            SourceComposableExtractor().extract(
                module = ":feature",
                sourceSet = "main",
                sourceRoots = listOf(sourceRoot),
                projectRoot = tempDir,
            )

        val list = assertNotNull(index.composables.singleOrNull())
        assertEquals("RankedList", list.name)
        assertEquals(listOf("items", "selected"), list.parameters.map { it.name })
        assertEquals(":feature:main:dev.example.RankedList#items:List<T>,selected:T", list.id)
    }
}
