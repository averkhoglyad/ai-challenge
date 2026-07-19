package io.averkhogliad.ai.challenge.week6.domain.error

import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import java.nio.file.Path as FilePath

sealed class DomainError(override val message: String) : io.averkhogliad.cli.repl.core.DomainError {

    class PathNotFound(path: String) : DomainError("Путь не найден: $path")

    class PathNotDirectory(path: String) : DomainError("Указанный путь не является директорией: $path")

    class ProjectNotFound(id: String) : DomainError("Проект не найден: $id")

    class ProjectAlreadyExists(path: String) : DomainError("Проект уже существует: $path")

    class ReleaseNotFound(id: String) : DomainError("Релиз не найден: $id")

    class InvalidVersionFormat(version: String) : DomainError("Некорректный формат версии: $version")

    class GitRangeNotFound(range: String) : DomainError("Git range не найден: $range")

    class NoCommitsInRange(range: String) : DomainError("В git range нет коммитов: $range")

    class RepositoryError(cause: String) : DomainError("Ошибка хранилища: $cause")

    class ReleasePersistencePartialFailure(cause: String) : DomainError(
        "CHANGELOG.md обновлён, но релиз не сохранён в БД: $cause"
    )

    class NoActiveProject : DomainError("Активный проект не выбран. Используйте /open <path>")

    class InvalidUrl(url: String) : DomainError("Некорректный URL: $url")

    class McpConnectionFailed(name: String, cause: String) : DomainError("Не удалось подключиться к '$name': $cause")

    class McpServerNotFound(name: String) : DomainError("MCP-сервер не найден: $name")

    // FileOps errors
    class FilePathTraversal(attemptedPath: String) : DomainError("Path traversal detected: $attemptedPath")

    class FileNotFound(path: RelativePath) : DomainError("File not found: $path")

    class FileOutsideSandbox(path: RelativePath, rootPath: FilePath) :
        DomainError("Access denied: path '$path' is outside project root")

    class FileExcludedDirectory(path: RelativePath, excludedDir: String) :
        DomainError("Access denied: '$path' is in excluded directory '$excludedDir'")

    class FileWriteDenied(path: RelativePath, reason: String) : DomainError("Write denied for '$path': $reason")

    class FileBinaryFile(path: RelativePath) : DomainError("Binary file, cannot read: $path")

    class FileTooLarge(path: RelativePath, sizeBytes: Long, limitBytes: Long) :
        DomainError("File too large: $path ($sizeBytes bytes, limit $limitBytes bytes)")

    class FileIOError(path: RelativePath?, cause: Throwable) :
        DomainError("IO error${path?.let { " for '$it'" } ?: ""}: ${cause.message}")

    companion object {
        fun pathNotFound(path: String): DomainError = PathNotFound(path)
        fun notDirectory(path: String): DomainError = PathNotDirectory(path)
        fun projectNotFound(id: String): DomainError = ProjectNotFound(id)
        fun alreadyExists(path: String): DomainError = ProjectAlreadyExists(path)
        fun releaseNotFound(id: String): DomainError = ReleaseNotFound(id)
        fun invalidVersionFormat(version: String): DomainError = InvalidVersionFormat(version)
        fun gitRangeNotFound(range: String): DomainError = GitRangeNotFound(range)
        fun noCommitsInRange(range: String): DomainError = NoCommitsInRange(range)
        fun repository(cause: String): DomainError = RepositoryError(cause)
        fun releasePersistencePartialFailure(cause: String): DomainError = ReleasePersistencePartialFailure(cause)
        fun noActiveProject(): DomainError = NoActiveProject()
        fun invalidUrl(url: String): DomainError = InvalidUrl(url)
        fun mcpConnectionFailed(name: String, cause: String): DomainError = McpConnectionFailed(name, cause)
        fun mcpServerNotFound(name: String): DomainError = McpServerNotFound(name)
    }
}
