package io.averkhogliad.ai.challenge.utils.llm

import io.averkhogliad.ai.challenge.utils.llm.DefaultLlmClient.Companion.MAX_ERROR_LENGTH
import io.averkhogliad.ai.challenge.utils.sanitizeForDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlin.time.toJavaDuration

/**
 * Реализация [LlmClient] для взаимодействия с OpenAI-совместимым LLM API.
 *
 * Инкапсулирует всю логику отправки HTTP-запросов, сериализации/десериализации
 * и обработки ошибок. Поддерживает все стандартные параметры Chat Completion API.
 *
 * Конфигурация предоставляется через [LlmClientConfig]:
 * - `baseUrl` - базовый URL API (например, "https://api.openai.com")
 * - `apiKey` - API ключ для аутентификации
 * - `model` - идентификатор модели (например, "gpt-4", "minimax/minimax-m3")
 * - `connectTimeout` - таймаут подключения
 * - `requestTimeout` - таймаут запроса
 * - `rateLimitEnabled` - включить/выключить rate limiting (по умолчанию true)
 * - `minInterval` - минимальный интервал между запросами
 * - `maxRequestsPerMinute` - максимальное количество запросов в минуту
 *
 * Rate limiting обеспечивает потокобезопасность через Mutex и проверяет два ограничения:
 * 1. Минимальный интервал между последовательными запросами
 * 2. Максимальное количество запросов в скользящем окне 1 минуты
 *
 * Пример использования:
 * ```kotlin
 * val config = LlmClientConfig.fromConfig(appConfig)
 * val client: LlmClient = DefaultLlmClient(config)
 * val response = client.chat(
 *     prompt = "Расскажи анекдот",
 *     parameters = ChatParameters(temperature = 0.7, maxTokens = 100)
 * )
 * println(response.content)
 * ```
 *
 * ⚠️ **Важно:** Всегда используйте [close] или `use {}` для освобождения ресурсов
 * (HTTP-клиент, thread pool, selector). Невызов `close()` приводит к утечке ресурсов.
 *
 * @param clientConfig Типизированная конфигурация с параметрами API
 */
class DefaultLlmClient(private val clientConfig: LlmClientConfig) : LlmClient {

    // Rate limiting state
    private data class RateLimitEntry(
        val requestId: Long,
        val timestamp: Instant
    )

    private var lastRequestTime: Instant = Instant.MIN

    // ArrayDeque эффективнее для скользящего окна (удаление устаревших элементов с начала)
    private val requestTimestamps: ArrayDeque<RateLimitEntry> = ArrayDeque()
    private val rateLimitMutex = Mutex()
    private val requestIdCounter = java.util.concurrent.atomic.AtomicLong(0)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(clientConfig.connectTimeout.toJavaDuration())
        .build()

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    companion object {
        private val API_KEY_PATTERN = Regex("\"api_key\"\\s*:\\s*\"[^\"]*\"")
        private val KEY_SK_PATTERN = Regex("\"key\"\\s*:\\s*\"sk-[^\"]+\"")
        private const val MAX_ERROR_LENGTH = 200
    }

    /**
     * Применяет rate-limit перед отправкой запроса.
     *
     * Проверяет два ограничения:
     * 1. Минимальный интервал между запросами ([LlmClientConfig.minInterval])
     * 2. Максимальное количество запросов в минуту ([LlmClientConfig.maxRequestsPerMinute])
     *
     * ## Двухфазный подход (атомарное резервирование)
     *
     * Чтобы другие корутины видели наше намерение и не нарушали лимиты,
     * слот резервируется ПЕРЕД задержкой:
     *
     * 1. **Под мьютексом:** вычисляем задержку, присваиваем уникальный `requestId`,
     *    регистрируем projected-время отправки (`RateLimitEntry`) в deque.
     *    Другие корутины видят зарезервированный слот.
     * 2. **Вне мьютекса:** выполняем `delay`.
     * 3. **Под мьютексом:** находим нашу запись по `requestId` и заменяем
     *    projected-время на фактическое. Это гарантирует, что даже при конкуренции
     *    корутин каждая заменяет только свою запись.
     *
     * Потокобезопасен: использует [Mutex] для защиты общего состояния.
     */
    private suspend fun applyRateLimit() {
        if (!clientConfig.rateLimitEnabled) return

        val requestId = requestIdCounter.incrementAndGet()

        // Фаза 1: вычисляем задержку и резервируем слот под мьютексом
        val delayMillis = rateLimitMutex.withLock {
            val now = Instant.now()
            val oneMinuteAgo = now.minusSeconds(60)

            // Очищаем устаревшие элементы
            while (requestTimestamps.isNotEmpty() && requestTimestamps.first().timestamp.isBefore(oneMinuteAgo)) {
                requestTimestamps.removeFirst()
            }

            var needDelay = 0L

            // Проверка минимального интервала между запросами
            val elapsed = java.time.Duration.between(lastRequestTime, now)
            val minIntervalJava = java.time.Duration.ofMillis(clientConfig.minInterval.inWholeMilliseconds)
            if (elapsed < minIntervalJava) {
                val remaining = minIntervalJava - elapsed
                if (remaining.toMillis() > needDelay) {
                    needDelay = remaining.toMillis()
                }
            }

            // Проверка максимального количества запросов в минуту
            if (requestTimestamps.size >= clientConfig.maxRequestsPerMinute) {
                val oldestRequest = requestTimestamps.first().timestamp
                val waitUntil = oldestRequest.plusSeconds(60)
                val waitDuration = java.time.Duration.between(now, waitUntil)
                if (waitDuration.toMillis() > needDelay) {
                    needDelay = waitDuration.toMillis()
                }
            }

            // Резервируем слот: projected-время = сейчас + задержка
            val projectedSendTime = now.plusMillis(needDelay)
            lastRequestTime = projectedSendTime
            requestTimestamps.add(RateLimitEntry(requestId, projectedSendTime))

            needDelay
        }

        // Фаза 2: ожидание вне мьютекса
        if (delayMillis > 0) {
            delay(delayMillis)
        }

        // Фаза 3: заменяем projected-время на фактическое (по requestId)
        rateLimitMutex.withLock {
            val now = Instant.now()

            // Находим и удаляем нашу запись по requestId (а не removeLast!)
            // Это исключает race condition при конкуренции корутин.
            // Поиск O(n) по ArrayDeque; при maxRequestsPerMinute ≤ 60 размер очереди мал
            // и линейный поиск не создаёт заметной нагрузки.
            val index = requestTimestamps.indexOfFirst { it.requestId == requestId }
            if (index >= 0) {
                requestTimestamps.removeAt(index)
            }

            // Регистрируем фактическое время отправки
            lastRequestTime = now
            requestTimestamps.add(RateLimitEntry(requestId, now))

            val oneMinuteAgo = now.minusSeconds(60)
            while (requestTimestamps.isNotEmpty() && requestTimestamps.first().timestamp.isBefore(oneMinuteAgo)) {
                requestTimestamps.removeFirst()
            }
        }
    }

    override suspend fun chat(
        prompt: String,
        systemPrompt: String?,
        parameters: ChatParameters,
        model: String?,
        tools: List<JsonObject>?
    ): ChatResponse {
        val effectiveModel = (model?.takeIf { it.isNotBlank() } ?: clientConfig.model)
        require(effectiveModel.isNotBlank()) { "Model ID cannot be blank" }

        val messages = buildList {
            systemPrompt?.let { add(ChatMessage.system(it)) }
            add(ChatMessage.user(prompt))
        }

        val request = ChatRequest.create(effectiveModel, messages, parameters, tools)
        return sendRequest(request)
    }

    override suspend fun chatWithMessages(
        messages: List<ChatMessage>,
        parameters: ChatParameters,
        model: String?,
        tools: List<JsonObject>?
    ): ChatResponse {
        val effectiveModel = (model?.takeIf { it.isNotBlank() } ?: clientConfig.model)
        require(effectiveModel.isNotBlank()) { "Model ID cannot be blank" }

        val request = ChatRequest.create(effectiveModel, messages, parameters, tools)
        return sendRequest(request)
    }

    private suspend fun sendRequest(request: ChatRequest): ChatResponse {
        applyRateLimit()  // Применяем rate-limit перед каждым запросом

        val requestBody = json.encodeToString(ChatRequest.serializer(), request)

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("${clientConfig.baseUrl}/v1/chat/completions"))
            .timeout(clientConfig.requestTimeout.toJavaDuration())
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${clientConfig.apiKey}")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = try {
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).await()
        } catch (e: CancellationException) {
            throw e  // Пробрасываем без изменений для корректной работы structured concurrency
        } catch (e: java.net.http.HttpConnectTimeoutException) {
            throw LlmException("Не удалось подключиться к API (таймаут соединения): ${e.message}", e)
        } catch (e: java.net.http.HttpTimeoutException) {
            throw LlmException("Превышено время ожидания ответа от API: ${e.message}", e)
        } catch (e: java.io.IOException) {
            throw LlmException("Сетевая ошибка при отправке запроса: ${e.message}", e)
        } catch (e: RuntimeException) {
            throw LlmException("Ошибка при отправке запроса: ${e.message}", e)
        }

        val responseBody = response.body()

        if (response.statusCode() !in 200..299) {
            val sanitizedError = sanitizeError(responseBody)
            throw LlmException("HTTP ${response.statusCode()}: $sanitizedError")
        }

        return parseResponse(responseBody)
    }

    internal fun parseResponse(responseBody: String): ChatResponse {
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

        val content = message["content"]?.jsonPrimitive?.contentOrNull

        val finishReason = firstChoice["finish_reason"]?.jsonPrimitive?.contentOrNull

        val usage = root["usage"]?.jsonObject?.let { usageObj ->
            ChatResponse.Usage(
                promptTokens = usageObj["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                completionTokens = usageObj["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                totalTokens = usageObj["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }

        val toolCalls = message["tool_calls"]?.jsonArray?.map { toolCallJson ->
            val obj = toolCallJson.jsonObject
            ToolCall(
                id = obj["id"]!!.jsonPrimitive.content,
                type = obj["type"]?.jsonPrimitive?.content ?: "function",
                function = FunctionCall(
                    name = obj["function"]!!.jsonObject["name"]!!.jsonPrimitive.content,
                    arguments = obj["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content
                )
            )
        }

        return ChatResponse(
            content = content,
            finishReason = finishReason,
            usage = usage,
            toolCalls = toolCalls
        )
    }

    /**
     * Санитизирует текст ошибки для безопасного логирования.
     *
     * Обрезает длинные ответы (до [MAX_ERROR_LENGTH] символов) и маскирует
     * потенциально чувствительные данные в два слоя:
     *
     * 1. [sanitizeForDisplay] — маскирует standalone Bearer-токены и sk-ключи (≥20 символов)
     *    в любом месте строки.
     * 2. Специализированные regex — маскируют JSON-поля `"api_key"` и `"key"` (когда
     *    значение начинается с `"sk-"`), которые могут быть пропущены первым слоем.
     *
     * Двухслойный подход гарантирует покрытие всех известных форматов чувствительных
     * данных в ответах API. Порядок важен: сначала общая маскировка, затем JSON-специфичная.
     *
     * Примечание: маскировка JSON-поля `"key"` со значением `sk-*` может задеть
     * безобидные error-ключи (например, `{"error": {"key": "sk-illustration"}}`),
     * но для учебного проекта ложная маскировка предпочтительнее риска утечки.
     */
    internal fun sanitizeError(responseBody: String): String {
        val maxLength = MAX_ERROR_LENGTH
        val truncated = if (responseBody.length > maxLength) {
            responseBody.take(maxLength) + "... (truncated)"
        } else {
            responseBody
        }

        return sanitizeForDisplay(truncated)
            .replace(API_KEY_PATTERN, "\"api_key\": \"***\"")
            // Маскируем JSON-поле "key" только когда значение начинается с "sk-".
            // Это дополняет sanitizeForDisplay, который маскирует standalone sk-ключи
            // длиной ≥20, но не JSON-поля с короткими значениями.
            .replace(KEY_SK_PATTERN, "\"key\": \"***\"")
    }

    /**
     * Закрывает HTTP-клиент и освобождает ресурсы.
     */
    override fun close() {
        // HttpClient implements AutoCloseable since Java 11;
        // releases underlying resources (selector, thread pool, etc.)
        httpClient.close()
    }
}
