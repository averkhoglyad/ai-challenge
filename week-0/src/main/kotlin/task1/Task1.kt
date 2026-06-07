package io.averkhogliad.ai.challenge.week0.task1

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.LlmClient
import io.averkhogliad.ai.challenge.utils.sanitizeForDisplay
import io.averkhogliad.ai.challenge.week0.Task
import kotlinx.coroutines.runBlocking

/**
 * Учебная задача #1: простой chat-completion к OpenAI-совместимому API.
 *
 * Это минимальная реализация — один запрос, один ответ.
 * Использует [LlmClient] для взаимодействия с API.
 *
 * Конфигурация (URL, API-ключ, модель, таймауты) загружается из [Config].
 * Таймауты задаются в формате ISO-8601 Duration (например, `PT15S`).
 */
class Task1(
    private val config: Config,
    private val llmClient: LlmClient
) : Task {

    override val title: String = "Task 1: простой chat-completion (single prompt)"

    private val terminal = Terminal()

    /**
     * Отправляет один user-промпт модели и возвращает текст ответа.
     *
     * @param prompt пользовательский промпт
     * @return текст ответа модели
     * @throws io.averkhogliad.ai.challenge.utils.llm.LlmException при ошибке API
     */
    suspend fun ask(prompt: String): String {
        val response = llmClient.chat(prompt)
        return response.content
    }

    /**
     * Демо-точка входа: отправляет промпт и печатает ответ с цветным выводом.
     *
     * Использует `runBlocking` для блокировки потока в консольном приложении,
     * так как REPL-цикл синхронный и не требует асинхронной обработки.
     */
    override fun run(prompt: String) {
        val baseUrl = config.get("api.base-url")
        val model = config.get("api.model")

        terminal.println(bold(cyan("🤖 Модель: ")) + white(model))
        terminal.println(bold(cyan("🌐 Endpoint: ")) + white(baseUrl))
        terminal.println()

        runBlocking {
            try {
                terminal.println(bold(yellow("⏳ Отправляю запрос к модели...")))
                
                val answer = ask(prompt)
                
                terminal.println()
                terminal.println(bold(green("✓ Ответ модели:")))
                terminal.println()
                terminal.println(white(answer))
                
            } catch (e: Exception) {
                terminal.println()
                terminal.println(bold(red("✗ Ошибка: ")) + red(sanitizeForDisplay(e.message ?: "неизвестная ошибка")))
                if (System.getProperty("debug") != null) {
                    e.printStackTrace()
                }
            }
        }
    }
}
