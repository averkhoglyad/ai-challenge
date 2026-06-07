package io.averkhogliad.ai.challenge.utils.llm

import io.averkhogliad.ai.challenge.utils.config.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Клиент для взаимодействия с OpenAI-совместимым LLM API.
 *
 * Инкапсулирует всю логику отправки HTTP-запросов, сериализации/десериализации
 * и обработки ошибок. Поддерживает все стандартные параметры Chat Completion API.
 *
 * Конфигурация загружается из [Config]:
 * - `api.base-url` - базовый URL API (например, "https://api.openai.com")
 * - `api.key` - API ключ для аутентификации
 * - `api.model` - идентификатор модели (например, "gpt-4", "minimax/minimax-m3")
 * - `api.connect-timeout` - таймаут подключения (ISO-8601 Duration, например "PT10S")
 * - `api.request-timeout` - таймаут запроса (ISO-8601 Duration, например "PT30S")
 * - `api.rate-limit.enabled` - включить/выключить rate limiting (по умолчанию true)
 * - `api.rate-limit.min-interval` - минимальный интервал между запросами (ISO-8601 Duration, по умолчанию "PT0.5S")
 * - `api.rate-limit.max-requests-per-minute` - максимальное количество запросов в минуту (по умолчанию 60)
 *
 * Rate limiting обеспечивает потокобезопасность через Mutex и проверяет два ограничения:
 * 1. Минимальный интервал между последовательными запросами
 * 2. Максимальное количество запросов в скользящем окне 1 минуты
 *
 * Пример использования:
 * ```kotlin
 * val client = LlmClient(config)
 * val response = client.chat(
 *     prompt = "Расскажи анекдот",
 *     parameters = ChatParameters(temperature = 0.7, maxTokens = 100)
 * )
 * println(response.content)
 * ```
 *
 * @property config Конфигурация с параметрами API
 */
class LlmClient(private val config: Config) : AutoCloseable {
    
    private val baseUrl: String = config.get("api.base-url")
    private val apiKey: String = config.get("api.key")
    private val model: String = config.get("api.model")
    
    private val connectTimeout: Duration = Duration.parse(config.get("api.connect-timeout"))
    private val requestTimeout: Duration = Duration.parse(config.get("api.request-timeout"))
    
    // Rate limiting
    private val rateLimitEnabled: Boolean = config.getOrDefault("api.rate-limit.enabled", "true").toBoolean()
    private val minInterval: Duration = Duration.parse(config.getOrDefault("api.rate-limit.min-interval", "PT0.5S"))
    private val maxRequestsPerMinute: Int = config.getOrDefault("api.rate-limit.max-requests-per-minute", "60").toInt()
    private var lastRequestTime: Instant = Instant.MIN
    private val requestTimestamps: MutableList<Instant> = mutableListOf()
    private val rateLimitMutex = Mutex()
    
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout.toJavaDuration())
        .build()
    
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    
    /**
     * Применяет rate-limit перед отправкой запроса.
     *
     * Проверяет два ограничения:
     * 1. Минимальный интервал между запросами (minInterval)
     * 2. Максимальное количество запросов в минуту (maxRequestsPerMinute)
     *
     * Если какое-либо ограничение нарушено, ждёт необходимое время.
     * Потокобезопасен: использует Mutex для защиты общего состояния.
     */
    private suspend fun applyRateLimit() {
        if (!rateLimitEnabled) return
        
        rateLimitMutex.withLock {
            val now = Instant.now()
            
            // 1. Проверка минимального интервала между запросами
            val elapsed = java.time.Duration.between(lastRequestTime, now)
            val minIntervalJava = java.time.Duration.ofMillis(minInterval.inWholeMilliseconds)
            
            if (elapsed < minIntervalJava) {
                val delayTime = minIntervalJava - elapsed
                delay(delayTime.toMillis())
            }
            
            // 2. Проверка максимального количества запросов в минуту
            val oneMinuteAgo = now.minusSeconds(60)
            // Удаляем timestamps старше 1 минуты
            requestTimestamps.removeAll { it.isBefore(oneMinuteAgo) }
            
            // Если превышен лимит запросов в минуту, ждём
            if (requestTimestamps.size >= maxRequestsPerMinute) {
                // Ждём до тех пор, пока самый старый запрос не станет старше 1 минуты
                val oldestRequest = requestTimestamps.minOrNull()
                if (oldestRequest != null) {
                    val waitUntil = oldestRequest.plusSeconds(60)
                    val waitDuration = java.time.Duration.between(Instant.now(), waitUntil)
                    if (waitDuration.toMillis() > 0) {
                        delay(waitDuration.toMillis())
                    }
                    // Очищаем старые timestamps после ожидания
                    val newOneMinuteAgo = Instant.now().minusSeconds(60)
                    requestTimestamps.removeAll { it.isBefore(newOneMinuteAgo) }
                }
            }
            
            // Записываем timestamp текущего запроса
            val requestTime = Instant.now()
            lastRequestTime = requestTime
            requestTimestamps.add(requestTime)
        }
    }
    
    /**
     * Отправляет запрос к LLM API и возвращает ответ.
     *
     * @param prompt Пользовательский промпт (сообщение от user)
     * @param systemPrompt Опциональное системное сообщение для установки контекста
     * @param parameters Параметры генерации (temperature, maxTokens, stop, responseFormat)
     * @param model Опциональный ID модели для переопределения (null — используется модель из конфига)
     * @return Ответ от модели с метаданными
     * @throws LlmException если произошла ошибка при запросе или парсинге ответа
     */
    suspend fun chat(
        prompt: String,
        systemPrompt: String? = null,
        parameters: ChatParameters = ChatParameters.DEFAULT,
        model: String? = null
    ): ChatResponse {
        val effectiveModel = (model?.takeIf { it.isNotBlank() } ?: this.model)
        require(effectiveModel.isNotBlank()) { "Model ID cannot be blank" }
        
        val messages = buildList {
            systemPrompt?.let { add(ChatMessage.system(it)) }
            add(ChatMessage.user(prompt))
        }
        
        val request = ChatRequest.create(effectiveModel, messages, parameters)
        return sendRequest(request)
    }
    
    /**
     * Отправляет запрос с произвольным списком сообщений.
     *
     * Полезно для multi-turn диалогов и сложных сценариев.
     *
     * @param messages Список сообщений в диалоге
     * @param parameters Параметры генерации
     * @param model Опциональный ID модели для переопределения (null — используется модель из конфига)
     * @return Ответ от модели с метаданными
     * @throws LlmException если произошла ошибка при запросе или парсинге ответа
     */
    suspend fun chatWithMessages(
        messages: List<ChatMessage>,
        parameters: ChatParameters = ChatParameters.DEFAULT,
        model: String? = null
    ): ChatResponse {
        val effectiveModel = (model?.takeIf { it.isNotBlank() } ?: this.model)
        require(effectiveModel.isNotBlank()) { "Model ID cannot be blank" }
        
        val request = ChatRequest.create(effectiveModel, messages, parameters)
        return sendRequest(request)
    }
    
    private suspend fun sendRequest(request: ChatRequest): ChatResponse {
        applyRateLimit()  // Применяем rate-limit перед каждым запросом
        
        val requestBody = json.encodeToString(ChatRequest.serializer(), request)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1/chat/completions"))
            .timeout(requestTimeout.toJavaDuration())
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        
        val response = try {
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw LlmException("Ошибка при отправке запроса: ${e.message}", e)
        }
        
        val responseBody = response.body()
        
        if (response.statusCode() !in 200..299) {
            val sanitizedError = sanitizeError(responseBody)
            throw LlmException("HTTP ${response.statusCode()}: $sanitizedError")
        }
        
        return parseResponse(responseBody)
    }
    
    private fun parseResponse(responseBody: String): ChatResponse {
        val jsonElement = try {
            json.parseToJsonElement(responseBody)
        } catch (e: Exception) {
            throw LlmException("Ошибка парсинга JSON ответа: ${e.message}", e)
        }
        
        val root = jsonElement.jsonObject
        
        val choices = root["choices"]?.jsonArray
            ?: throw LlmException("API response missing 'choices' field")
        
        if (choices.isEmpty()) {
            throw LlmException("API response 'choices' array is empty")
        }
        
        val firstChoice = choices.first().jsonObject
        
        val message = firstChoice["message"]?.jsonObject
            ?: throw LlmException("API response missing 'message' field in first choice")
        
        val content = message["content"]?.jsonPrimitive?.content
            ?: throw LlmException("API response missing 'content' field in message")
        
        val finishReason = firstChoice["finish_reason"]?.jsonPrimitive?.contentOrNull
        
        val usage = root["usage"]?.jsonObject?.let { usageObj ->
            ChatResponse.Usage(
                promptTokens = usageObj["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                completionTokens = usageObj["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                totalTokens = usageObj["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }
        
        return ChatResponse(
            content = content,
            finishReason = finishReason,
            usage = usage
        )
    }
    
    /**
     * Санитизирует текст ошибки для безопасного логирования.
     * Обрезает длинные ответы и маскирует потенциально чувствительные данные.
     */
    private fun sanitizeError(responseBody: String): String {
        val maxLength = 200
        val truncated = if (responseBody.length > maxLength) {
            responseBody.take(maxLength) + "... (truncated)"
        } else {
            responseBody
        }
        
        return truncated
            .replace(Regex("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*"), "Bearer ***")
            .replace(Regex("\"api_key\"\\s*:\\s*\"[^\"]*\""), "\"api_key\": \"***\"")
            .replace(Regex("\"key\"\\s*:\\s*\"[^\"]*\""), "\"key\": \"***\"")
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

/**
 * Исключение, возникающее при ошибках в работе с LLM API.
 */
class LlmException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
