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
        var indexFileArgument: Path? = null
        var transport = ServerTransport.Stdio
        var port = 3000
        var index = 0

        while (index < args.size) {
            when (val arg = args[index]) {
                "--project-root" -> {
                    projectRoot =
                        args
                            .requiredValue(index, arg)
                            .let(Path::of)
                            .toAbsolutePath()
                            .normalize()
                    index += 2
                }

                "--index-file" -> {
                    indexFileArgument =
                        args
                            .requiredValue(index, arg)
                            .let(Path::of)
                    index += 2
                }

                "--transport" -> {
                    transport =
                        when (args.requiredValue(index, arg).lowercase()) {
                            "stdio" -> ServerTransport.Stdio
                            "http" -> ServerTransport.Http
                            else -> throw IllegalArgumentException("--transport must be 'stdio' or 'http'")
                        }
                    index += 2
                }

                "--port" -> {
                    port =
                        args
                            .requiredValue(index, arg)
                            .toIntOrNull()
                            ?: throw IllegalArgumentException("--port must be an integer")
                    if (port !in MIN_PORT..MAX_PORT) {
                        throw IllegalArgumentException("--port must be between $MIN_PORT and $MAX_PORT")
                    }
                    index += 2
                }

                "--help", "-h" -> {
                    throw HelpRequested(usage())
                }

                else -> {
                    throw IllegalArgumentException("Unknown argument: $arg\n${usage()}")
                }
            }
        }

        return ComposeExposeServerOptions(
            projectRoot = projectRoot,
            indexFile = indexFileArgument?.resolveAgainst(projectRoot) ?: projectRoot.resolve("build/composeExpose/all-composables.json"),
            transport = transport,
            port = port,
        )
    }

    fun usage(): String =
        """
        Usage: compose-expose-mcp [options]

        Options:
          --project-root <path>   Gradle project root. Defaults to current directory.
          --index-file <path>     Index JSON. Relative paths resolve from --project-root.
          --transport <stdio|http> MCP transport. Defaults to stdio.
          --port <port>           HTTP port from 1 to 65535 when --transport http is used. Defaults to 3000.
          -h, --help              Show this help.
        """.trimIndent()

    private fun Array<String>.requiredValue(
        index: Int,
        flag: String,
    ): String = getOrNull(index + 1) ?: throw IllegalArgumentException("Missing value for $flag")

    private fun Path.resolveAgainst(projectRoot: Path): Path =
        if (isAbsolute) {
            normalize()
        } else {
            projectRoot.resolve(this).normalize()
        }

    private const val MIN_PORT = 1
    private const val MAX_PORT = 65535
}

class HelpRequested(
    message: String,
) : RuntimeException(message)
