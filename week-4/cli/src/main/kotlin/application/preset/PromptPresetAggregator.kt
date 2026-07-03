package io.averkhogliad.ai.challenge.week4.cli.application.preset

import io.averkhogliad.ai.challenge.week4.cli.application.service.MCPService
import io.averkhogliad.ai.challenge.week4.cli.domain.model.PromptPreset
import io.averkhogliad.ai.challenge.week4.cli.domain.model.PromptSource
import io.averkhogliad.ai.challenge.week4.cli.domain.service.PromptPresetProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Агрегатор preset'ов из нескольких источников.
 *
 * Объединяет BUILTIN preset'ы (из resources/presets/) и MCP preset'ы (с серверов).
 * Загружает параллельно через [coroutineScope].
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация сбора данных из разных источников
 * - **Functional Core / Imperative Shell** — чистый сбор, I/O в вызываемых портах
 */
class PromptPresetAggregator(
    private val builtinProvider: PromptPresetProvider,
    private val mcpService: MCPService
) {

    /**
     * Собирает все preset'ы из BUILTIN и MCP источников.
     *
     * Graceful degradation: ошибки одного источника не прерывают общий сбор.
     *
     * @return объединённый список preset'ов
     */
    suspend fun aggregate(): List<PromptPreset> = coroutineScope {
        val builtinDeferred = async { loadBuiltinPresets() }
        val mcpDeferred = async { loadMcpPresets() }

        builtinDeferred.await() + mcpDeferred.await()
    }

    private suspend fun loadBuiltinPresets(): List<PromptPreset> = try {
        builtinProvider.load()
    } catch (e: Exception) {
        System.err.println("[PRESET-AGGREGATOR] Ошибка загрузки BUILTIN preset'ов: ${e.message}")
        emptyList()
    }

    private suspend fun loadMcpPresets(): List<PromptPreset> {
        return try {
            val servers = mcpService.getAllServersForLlm()
            if (servers.isEmpty()) return emptyList()

            val result = mutableListOf<PromptPreset>()
            for (server in servers) {
                if (!server.isConnected) continue
                try {
                    val prompts = mcpService.getPrompts(server.id).getOrElse { emptyList() }
                    for (prompt in prompts) {
                        val defaultArgs = prompt.arguments
                            .filter { it.required }
                            .associate { it.name to "{${it.name}}" }

                        val messages = if (defaultArgs.isNotEmpty()) {
                            mcpService.getPrompt(server.id, prompt.name, defaultArgs).getOrElse { emptyList() }
                        } else {
                            mcpService.getPrompt(server.id, prompt.name).getOrElse { emptyList() }
                        }

                        val content = messages.joinToString("\n") { msg ->
                            when (msg.content) {
                                is io.averkhogliad.ai.challenge.week4.cli.domain.model.McpPromptContent.Text ->
                                    msg.content.text

                                else -> ""
                            }
                        }

                        if (content.isNotBlank()) {
                            result.add(
                                PromptPreset(
                                    name = prompt.name,
                                    description = prompt.description ?: "",
                                    instruction = content,
                                    source = PromptSource.MCP,
                                    tags = emptyList()
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    System.err.println("[PRESET-AGGREGATOR] Ошибка загрузки preset'ов с сервера ${server.name}: ${e.message}")
                }
            }
            result
        } catch (e: Exception) {
            System.err.println("[PRESET-AGGREGATOR] Ошибка загрузки MCP preset'ов: ${e.message}")
            emptyList()
        }
    }
}
