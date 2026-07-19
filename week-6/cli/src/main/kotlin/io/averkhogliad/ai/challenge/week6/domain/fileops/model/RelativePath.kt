package io.averkhogliad.ai.challenge.week6.domain.fileops.model

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import java.nio.file.Path

@JvmInline
value class RelativePath private constructor(val value: String) {

    override fun toString(): String = value

    fun toAbsolutePath(rootPath: Path): Path = rootPath.resolve(value).normalize()

    companion object {
        fun from(path: String, rootPath: Path): DomainResult<RelativePath> {
            val trimmed = path.trim()

            if (trimmed.isEmpty()) {
                return DomainResult.Failure(DomainError.FilePathTraversal(""))
            }

            // Absolute path detection: Unix-style or Windows drive letter
            if (trimmed.startsWith("/") || trimmed.matches(Regex("^[a-zA-Z]:", RegexOption.IGNORE_CASE))) {
                return DomainResult.Failure(DomainError.FilePathTraversal(trimmed))
            }

            if (trimmed.contains('\\')) {
                return DomainResult.Failure(DomainError.FilePathTraversal(trimmed))
            }

            if (trimmed.contains("//")) {
                return DomainResult.Failure(DomainError.FilePathTraversal(trimmed))
            }

            val segments = trimmed.split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) {
                return DomainResult.Failure(DomainError.FilePathTraversal(trimmed))
            }

            if (segments.any { it == ".." || it == "." }) {
                return DomainResult.Failure(DomainError.FilePathTraversal(trimmed))
            }

            val normalized = segments.joinToString("/")

            val resolved = rootPath.resolve(normalized).normalize()
            if (!resolved.startsWith(rootPath)) {
                return DomainResult.Failure(
                    DomainError.FileOutsideSandbox(RelativePath(normalized), rootPath)
                )
            }

            return DomainResult.Success(RelativePath(normalized))
        }
    }
}
