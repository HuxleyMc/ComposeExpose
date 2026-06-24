package dev.huxleymc.composeexpose.core

object SourceSetDetector {
    private val androidSourcePathRegex = Regex("""(?:^|/)src/([^/]+)/(?:kotlin|java)(?:/|$)""")

    fun detect(path: String): String? {
        return androidSourcePathRegex.find(path)?.groupValues?.get(1)
    }
}
