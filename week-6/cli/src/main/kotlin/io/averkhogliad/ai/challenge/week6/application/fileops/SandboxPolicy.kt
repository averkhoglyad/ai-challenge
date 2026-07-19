package io.averkhogliad.ai.challenge.week6.application.fileops

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath

enum class FileOperation { Read, Write, Search, List }

class ExclusionList(
    private val patterns: List<String> = DEFAULT_EXCLUSIONS,
    private val custom: List<String> = emptyList(),
) {
    val all: List<String> = patterns + custom

    private val dirs: List<String> = all
        .map { it.trimEnd('/') }
        .filter { !it.startsWith("*.") }
        .map { p ->
            when {
                p.startsWith("./") -> p.removePrefix("./")
                p.startsWith("/") -> p.removePrefix("/")
                else -> p
            }
        }

    private val extensions: List<String> = all
        .filter { it.startsWith("*.") }
        .map { it.removePrefix("*") }

    fun matches(path: RelativePath): String? {
        val p = path.value

        for (dir in dirs) {
            if (p == dir || p.startsWith("$dir/")) return dir
        }

        for (ext in extensions) {
            if (p.endsWith(ext)) return ext
        }

        return null
    }

    companion object {
        val DEFAULT_EXCLUSIONS = listOf(
            ".git", ".idea", ".gradle", ".vscode", ".veai",
            "node_modules", "build", "target", "dist", "out",
            ".env", "*.key", "*.pem", "*.p12", "*.jks", "*.keystore",
        )

        fun fromProject(repo: ProjectSettingsRepository, projectId: String): ExclusionList {
            val custom = repo.loadExclusions(projectId)
            return ExclusionList(custom = custom)
        }
    }
}

class SandboxPolicy(
    private val exclusionList: ExclusionList = ExclusionList(),
) {
    fun check(path: RelativePath, operation: FileOperation): DomainResult<Unit> {
        val excluded = exclusionList.matches(path)
        if (excluded != null) {
            val error: DomainError = if (operation == FileOperation.Write) {
                DomainError.FileWriteDenied(path, "excluded directory: $excluded")
            } else {
                DomainError.FileExcludedDirectory(path, excluded)
            }
            return DomainResult.Failure(error)
        }

        return DomainResult.Success(Unit)
    }
}
