package io.averkhogliad.ai.challenge.week6.domain.error

sealed class DomainError(override val message: String) : io.averkhogliad.cli.repl.core.DomainError {

    class PathNotFound(path: String) : DomainError("Путь не найден: $path")

    class PathNotDirectory(path: String) : DomainError("Указанный путь не является директорией: $path")

    class ProjectNotFound(id: String) : DomainError("Проект не найден: $id")

    class ProjectAlreadyExists(path: String) : DomainError("Проект уже существует: $path")

    class RepositoryError(cause: String) : DomainError("Ошибка хранилища: $cause")

    class NoActiveProject : DomainError("Активный проект не выбран. Используйте /open <path>")

    class InvalidUrl(url: String) : DomainError("Некорректный URL: $url")

    class McpConnectionFailed(name: String, cause: String) : DomainError("Не удалось подключиться к '$name': $cause")

    class McpServerNotFound(name: String) : DomainError("MCP-сервер не найден: $name")

    companion object {
        fun pathNotFound(path: String): DomainError = PathNotFound(path)
        fun notDirectory(path: String): DomainError = PathNotDirectory(path)
        fun projectNotFound(id: String): DomainError = ProjectNotFound(id)
        fun alreadyExists(path: String): DomainError = ProjectAlreadyExists(path)
        fun repository(cause: String): DomainError = RepositoryError(cause)
        fun noActiveProject(): DomainError = NoActiveProject()
        fun invalidUrl(url: String): DomainError = InvalidUrl(url)
        fun mcpConnectionFailed(name: String, cause: String): DomainError = McpConnectionFailed(name, cause)
        fun mcpServerNotFound(name: String): DomainError = McpServerNotFound(name)
    }
}
