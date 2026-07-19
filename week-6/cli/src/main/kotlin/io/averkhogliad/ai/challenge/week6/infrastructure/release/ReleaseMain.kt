package io.averkhogliad.ai.challenge.week6.infrastructure.release

data class ReleaseCommandLine(
    val projectId: String,
    val version: String?,
    val range: String?,
    val output: String?,
)

object ReleaseMain {
    fun parse(args: Array<String>): Result<ReleaseCommandLine> = runCatching {
        require(args.firstOrNull() == "--release") { "Expected --release" }
        val values = mutableMapOf<String, String>()
        var index = 1
        while (index < args.size) {
            val option = args[index]
            require(option in OPTIONS) { "Unknown option: $option" }
            require(index + 1 < args.size) { "Missing value for $option" }
            require(option !in values) { "Duplicate option: $option" }
            values[option] = args[index + 1]
            index += 2
        }
        val projectId = values[PROJECT_ID] ?: error("Missing required option: --project-id")
        ReleaseCommandLine(projectId, values[VERSION], values[RANGE], values[OUTPUT])
    }

    private const val PROJECT_ID = "--project-id"
    private const val VERSION = "--version"
    private const val RANGE = "--range"
    private const val OUTPUT = "--output"
    private val OPTIONS = setOf(PROJECT_ID, VERSION, RANGE, OUTPUT)
}
