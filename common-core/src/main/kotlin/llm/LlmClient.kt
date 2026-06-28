package io.averkhogliad.ai.challenge.utils.llm

/**
 * Интерфейс клиента для взаимодействия с OpenAI-совместимым LLM API.
 *
 * Предоставляет два основных метода:
 * - [chat] — отправка одного пользовательского промпта
 * - [chatWithMessages] — отправка произвольного списка сообщений (multi-turn, few-shot)
 *
 * Реализация: [DefaultLlmClient]
 *
 * Пример использования:
 * ```kotlin
 * val client: LlmClient = DefaultLlmClient(config)
 * val response = client.chat(
 *     prompt = "Расскажи анекдот",
 *     parameters = ChatParameters(temperature = 0.7, maxTokens = 100)
 * )
 * println(response.content)
 * ```
 */
interface LlmClient : AutoCloseable {

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
    ): ChatResponse

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
    ): ChatResponse
}

/**
 * Исключение, возникающее при ошибках в работе с LLM API.
 */
class LlmException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
