package dev.huxleymc.composeexpose.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceSetDetectorTest {
    @Test
    fun `detects Android source set from kotlin and java source paths`() {
        assertEquals(
            "main",
            SourceSetDetector.detect("/repo/app/src/main/kotlin/dev/example/Card.kt"),
        )
        assertEquals(
            "freeDebug",
            SourceSetDetector.detect("/repo/app/src/freeDebug/java/dev/example/Card.kt"),
        )
        assertEquals(
            "paid",
            SourceSetDetector.detect("app/src/paid/kotlin/dev/example/Card.kt"),
        )
    }

    @Test
    fun `returns null when path is not an Android source path`() {
        assertNull(SourceSetDetector.detect("/repo/app/build/generated/ksp/debug/kotlin/Card.kt"))
        assertNull(SourceSetDetector.detect("/repo/app/src/main/resources/strings.xml"))
    }
}
