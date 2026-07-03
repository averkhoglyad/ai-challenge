package io.averkhogliad.ai.challenge.week4.cli.application.service

import io.averkhogliad.ai.challenge.week4.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MCPConnectionManager
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MCPServerRepository
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.net.URI

/**
 * Сервис для управления MCP-серверами.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация операций с MCP-серверами
 * - **Не зависит** от UI (CLI)
 * - **Зависит только** от domain ports [MCPServerRepository] и [MCPConnectionManager]
 *
 * ## Обработка ошибок
 * Все методы возвращают [Result] с типизированными [MCPOperationError]
 * для точной обработки в CLI-слое.
 */
class MCPService(
    private val serverRepository: MCPServerRepository,
    private val connectionManager: MCPConnectionManager
) {
    /**
     * Добавить новый MCP-сервер.
     *
     * @param name Имя сервера (1-50 символов, только [a-zA-Z0-9\\-])
     * @param transport Транспорт (Stdio или StreamableHttp)
     * @return [Result] с созданным [MCPServerConfig] или [MCPOperationError]
     */
    suspend fun addServer(name: String, transport: MCPTransport): Result<MCPServerConfig> {
        // Validate transport-specific requirements
        when (transport) {
            is MCPTransport.Stdio -> {
                if (transport.command.isBlank())
                    return Result.failure(MCPOperationError.InvalidCommand("Command must not be blank"))
            }

            is MCPTransport.StreamableHttp -> {
                if (!isValidUrl(transport.url))
                    return Result.failure(MCPOperationError.InvalidUrl(transport.url))
            }
        }

        // MCPServerConfig.create validates name format (1-50 chars, alphanumeric + hyphens)
        return try {
            val existing = serverRepository.findByName(name)
            if (existing != null)
                return Result.failure(MCPOperationError.AlreadyExists(name))

            val config = MCPServerConfig.create(name, transport)
            val saved = serverRepository.save(config)
            Result.success(saved)
        } catch (e: IllegalArgumentException) {
            Result.failure(MCPOperationError.InvalidName(e.message ?: "Invalid name"))
        }
    }

    /**
     * Удалить MCP-сервер.
     *
     * Отключает сервер, если он подключён, затем удаляет из репозитория.
     *
     * @param id Идентификатор сервера
     * @return [Result] с Unit или [MCPOperationError]
     */
    suspend fun removeServer(id: ModelId): Result<String> {
        val config = serverRepository.findById(id)
            ?: return Result.failure(MCPOperationError.NotFound(id.value))

        // Disconnect if connected
        if (connectionManager.isConnected(id)) {
            connectionManager.disconnect(id)
        }

        serverRepository.delete(id)
        return Result.success(config.name)
    }

    /**
     * Получить список всех серверов с их текущим статусом подключения.
     * Возвращает ТОЛЬКО пользовательские сервера (из репозитория).
     *
     * @return Список [ServerWithStatus]
     */
    suspend fun listServers(): List<ServerWithStatus> {
        val configs = serverRepository.findAll()
        return configs.map { config ->
            val status = connectionManager.getStatus(config.id)
            ServerWithStatus(config = config, status = status)
        }
    }

    /**
     * Получить список ВСЕХ серверов (пользовательские + системные) для передачи в LLM.
     */
    suspend fun getAllServersForLlm(): List<McpServerRef> {
        val userServers = listServers().map { McpServerRef(it.config.id, it.config.name, it.status) }
        val systemIds = connectionManager.getSystemServerIds()
        val systemServers = systemIds.map { id ->
            McpServerRef(id, id.value, connectionManager.getStatus(id))
        }
        return userServers + systemServers
    }

    /**
     * Подключиться к MCP-серверу.
     *
     * @param id Идентификатор сервера
     * @return [Result] с [MCPConnectionState] или [MCPOperationError]
     */
    suspend fun connect(id: ModelId): Result<MCPConnectionState> {
        val state = connectionManager.connect(id)
        return when (state) {
            is MCPConnectionState.Failed -> {
                when (state.reason) {
                    MCPFailureReason.NOT_FOUND -> Result.failure(MCPOperationError.NotFound(id.value))
                    MCPFailureReason.DISABLED -> Result.failure(MCPOperationError.ServerDisabled(id.value))
                    MCPFailureReason.TRANSPORT_ERROR -> Result.failure(MCPOperationError.ConnectionFailed(state.error))
                }
            }

            else -> Result.success(state)
        }
    }

    /**
     * Отключиться от MCP-сервера.
     *
     * @param id Идентификатор сервера
     */
    suspend fun disconnect(id: ModelId) {
        connectionManager.disconnect(id)
    }

    /**
     * Получить список инструментов MCP-сервера.
     *
     * @param id Идентификатор сервера
     * @return [Result] со списком [MCPTool] или [MCPOperationError]
     */
    suspend fun getTools(id: ModelId): Result<List<MCPTool>> {
        if (!connectionManager.isConnected(id))
            return Result.failure(MCPOperationError.NotConnected(id.value))

        val tools = connectionManager.getTools(id)
        return Result.success(tools)
    }

    /**
     * Выполнить инструмент на MCP-сервере.
     *
     * @param serverId Идентификатор сервера
     * @param name Имя инструмента
     * @param arguments Аргументы инструмента
     * @return [Result] с текстовым результатом выполнения или [MCPOperationError]
     */
    suspend fun callTool(serverId: ModelId, name: String, arguments: Map<String, Any?>): Result<String> {
        if (!connectionManager.isConnected(serverId))
            return Result.failure(MCPOperationError.NotConnected(serverId.value))

        return try {
            val result = connectionManager.callTool(serverId, name, arguments)
            val textContent = result.content.filterIsInstance<TextContent>().joinToString { it.text }
            Result.success(textContent)
        } catch (e: Exception) {
            Result.failure(MCPOperationError.ConnectionFailed(e.message ?: "Unknown error"))
        }
    }

    /**
     * Получить список prompts с MCP-сервера.
     *
     * @param serverId Идентификатор сервера
     * @return [Result] со списком [McpPrompt] или [MCPOperationError]
     */
    suspend fun getPrompts(serverId: ModelId): Result<List<McpPrompt>> {
        if (!connectionManager.isConnected(serverId))
            return Result.failure(MCPOperationError.NotConnected(serverId.value))

        return try {
            val prompts = connectionManager.getPrompts(serverId)
            Result.success(prompts)
        } catch (e: Exception) {
            Result.failure(MCPOperationError.ConnectionFailed(e.message ?: "Unknown error"))
        }
    }

    /**
     * Получить содержимое prompt с подставленными аргументами.
     *
     * @param serverId Идентификатор сервера
     * @param name Имя prompt
     * @param arguments Аргументы для подстановки в шаблон
     * @return [Result] со списком [McpPromptMessage] или [MCPOperationError]
     */
    suspend fun getPrompt(
        serverId: ModelId,
        name: String,
        arguments: Map<String, String> = emptyMap()
    ): Result<List<McpPromptMessage>> {
        if (!connectionManager.isConnected(serverId))
            return Result.failure(MCPOperationError.NotConnected(serverId.value))

        return try {
            val messages = connectionManager.getPrompt(serverId, name, arguments)
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(MCPOperationError.ConnectionFailed(e.message ?: "Unknown error"))
        }
    }

    // ──── Private helpers ────

    /**
     * Проверить, что URL является валидным HTTP(S) URL.
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme
            scheme == "http" || scheme == "https"
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Статус сервера вместе с его конфигурацией.
 *
 * @property config Конфигурация MCP-сервера
 * @property status Текущий статус подключения
 */
data class ServerWithStatus(
    val config: MCPServerConfig,
    val status: MCPConnectionState
)

/**
 * Типизированные ошибки операций с MCP-серверами.
 *
 * ## Архитектурная роль
 * - **Application Layer** — ошибки прикладного уровня
 * - Наследуют [Exception] для совместимости с try-catch и [Result]
 * - Сообщения на русском языке, готовые для рендеринга
 */
sealed class MCPOperationError(message: String) : Exception(message) {
    /** Сервер с указанным ID не найден */
    class NotFound(id: String) : MCPOperationError("MCP-сервер с ID \"$id\" не найден")

    /** Сервер с таким именем уже существует */
    class AlreadyExists(name: String) : MCPOperationError("MCP-сервер с именем \"$name\" уже существует")

    /** Некорректное имя сервера */
    class InvalidName(detail: String) : MCPOperationError("Некорректное имя сервера: $detail")

    /** Некорректный URL для StreamableHttp транспорта */
    class InvalidUrl(url: String) : MCPOperationError("Некорректный URL: \"$url\". Ожидается HTTP(S) URL")

    /** Некорректная команда для Stdio транспорта */
    class InvalidCommand(detail: String) : MCPOperationError("Некорректная команда: $detail")

    /** Сервер отключён (disabled) */
    class ServerDisabled(id: String) : MCPOperationError("MCP-сервер с ID \"$id\" отключён")

    /** Сервер не подключён — нельзя получить инструменты */
    class NotConnected(id: String) : MCPOperationError("MCP-сервер с ID \"$id\" не подключён")

    /** Ошибка подключения к серверу */
    class ConnectionFailed(detail: String) : MCPOperationError("Ошибка подключения: $detail")
}
