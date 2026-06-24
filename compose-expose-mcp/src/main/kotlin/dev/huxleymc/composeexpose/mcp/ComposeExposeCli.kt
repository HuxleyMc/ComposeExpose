package dev.huxleymc.composeexpose.mcp

import java.nio.file.Path

enum class ServerTransport {
    Stdio,
    Http,
}

data class ComposeExposeServerOptions(
    val projectRoot: Path,
    val indexFile: Path,
    val transport: ServerTransport,
    val port: Int,
)

object ComposeExposeCli {
    fun parse(
        args: Array<String>,
        defaultProjectRoot: Path = Path.of(System.getProperty("user.dir")),
    ): ComposeExposeServerOptions {
        var projectRoot = defaultProjectRoot.toAbsolutePath().normalize()
        var indexFile: Path? = null
        var transport = ServerTransport.Stdio
        var port = 3000
        var index = 0

        while (index < args.size) {
            when (val arg = args[index]) {
                "--project-root" -> {
                    projectRoot = args.requiredValue(index, arg).let(Path::of).toAbsolutePath().normalize()
                    index += 2
                }
                "--index-file" -> {
                    indexFile = args.requiredValue(index, arg).let(Path::of).toAbsolutePath().normalize()
                    index += 2
                }
                "--transport" -> {
                    transport = when (args.requiredValue(index, arg).lowercase()) {
                        "stdio" -> ServerTransport.Stdio
                        "http" -> ServerTransport.Http
                        else -> throw IllegalArgumentException("--transport must be 'stdio' or 'http'")
                    }
                    index += 2
                }
                "--port" -> {
                    port = args.requiredValue(index, arg).toIntOrNull()
                        ?: throw IllegalArgumentException("--port must be an integer")
                    index += 2
                }
                "--help", "-h" -> throw HelpRequested(usage())
                else -> throw IllegalArgumentException("Unknown argument: $arg\n${usage()}")
            }
        }

        return ComposeExposeServerOptions(
            projectRoot = projectRoot,
            indexFile = indexFile ?: projectRoot.resolve("build/composeExpose/all-composables.json"),
            transport = transport,
            port = port,
        )
    }

    fun usage(): String {
        return """
            Usage: compose-expose-mcp [options]

            Options:
              --project-root <path>   Gradle project root. Defaults to current directory.
              --index-file <path>     Index JSON. Defaults to build/composeExpose/all-composables.json.
              --transport <stdio|http> MCP transport. Defaults to stdio.
              --port <port>           HTTP port when --transport http is used. Defaults to 3000.
              -h, --help              Show this help.
        """.trimIndent()
    }

    private fun Array<String>.requiredValue(index: Int, flag: String): String {
        return getOrNull(index + 1) ?: throw IllegalArgumentException("Missing value for $flag")
    }
}

class HelpRequested(message: String) : RuntimeException(message)
