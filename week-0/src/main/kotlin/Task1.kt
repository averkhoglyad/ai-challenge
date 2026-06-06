package io.averkhogliad.ai.challenge.week0

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.utils.config.Config
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Учебная задача #1: простой chat-completion к OpenAI-совместимому API.
 *
 * Это минимальная реализация — один запрос, один ответ.
 * Конфигурация (URL, API-ключ, модель, таймауты) загружается из [Config].
 *
 * Таймауты задаются в формате ISO-8601 Duration:
 * - `PT15S` — 15 секунд
 * - `PT1M` — 1 минута
 * - `PT1M30S` — 1 минута 30 секунд
 *
 * Реализует [AutoCloseable] для корректного освобождения ресурсов HTTP-клиента.
 */
class Task1(private val config: Config) : Task, AutoCloseable {

    override val title: String = "Task 1: простой chat-completion (single prompt)"

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<Message>,
    ) {
        @Serializable
        data class Message(val role: String, val content: String)
    }

    private val connectTimeout: Duration = Duration.parse(config.get("api.connect-timeout"))
    private val requestTimeout: Duration = Duration.parse(config.get("api.request-timeout"))

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout.toJavaDuration())
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true }

    private val terminal = Terminal()

    /**
     * Отправляет один user-промпт модели и возвращает текст ответа.
     *
     * @throws IllegalStateException если ответ API имеет некорректную структуру
     * @throws RuntimeException если HTTP-запрос завершился ошибкой
     */
    suspend fun ask(prompt: String): String {
        val baseUrl = config.get("api.base-url")
        val apiKey = config.get("api.key")
        val model = config.get("api.model")

        val body = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                model = model,
                messages = listOf(ChatRequest.Message(role = "user", content = prompt)),
            ),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1/chat/completions"))
            .timeout(requestTimeout.toJavaDuration())
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val responseBody = response.body()

        if (response.statusCode() !in 200..299) {
            // Не логируем полный responseBody, так как он может содержать чувствительные данные
            throw RuntimeException("HTTP ${response.statusCode()}: ${sanitizeError(responseBody)}")
        }

        // Безопасный парсинг с информативными ошибками
        val root = json.parseToJsonElement(responseBody).jsonObject
        
        val choices = root["choices"]?.jsonArray 
            ?: throw IllegalStateException("API response missing 'choices' field")
        
        if (choices.isEmpty()) {
            throw IllegalStateException("API response 'choices' array is empty")
        }
        
        val firstChoice = choices.first().jsonObject
        
        val message = firstChoice["message"]?.jsonObject 
            ?: throw IllegalStateException("API response missing 'message' field in first choice")
        
        val content = message["content"]?.jsonPrimitive?.content 
            ?: throw IllegalStateException("API response missing 'content' field in message")
        
        return content
    }

    /**
     * Санитизирует текст ошибки для безопасного логирования.
     * Обрезает длинные ответы и маскирует потенциально чувствительные данные.
     */
    private fun sanitizeError(responseBody: String): String {
        // Ограничиваем длину сообщения
        val maxLength = 200
        val truncated = if (responseBody.length > maxLength) {
            responseBody.take(maxLength) + "... (truncated)"
        } else {
            responseBody
        }
        
        // Маскируем потенциальные API-ключи и токены (простая эвристика)
        return truncated
            .replace(Regex("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*"), "Bearer ***")
            .replace(Regex("\"api_key\"\\s*:\\s*\"[^\"]*\""), "\"api_key\": \"***\"")
            .replace(Regex("\"key\"\\s*:\\s*\"[^\"]*\""), "\"key\": \"***\"")
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
        terminal.println(gray("⏱️  Таймауты: connect=$connectTimeout, request=$requestTimeout"))
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

    /**
     * Санитизирует сообщение об ошибке для отображения пользователю.
     */
    private fun sanitizeForDisplay(message: String): String {
        return message
            .replace(Regex("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*"), "Bearer ***")
            .replace(Regex("sk-[A-Za-z0-9]{20,}"), "sk-***")
    }

    /**
     * Закрывает HTTP-клиент и освобождает ресурсы.
     */
    override fun close() {
        // HttpClient в Java 11+ не имеет явного метода close(),
        // но реализация может освобождать ресурсы при сборке мусора.
        // Оставляем метод для совместимости с AutoCloseable и будущих расширений.
    }
}
