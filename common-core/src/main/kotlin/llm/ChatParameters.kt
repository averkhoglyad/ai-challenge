package io.averkhogliad.ai.challenge.utils.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Параметры запроса к LLM API.
 *
 * Все параметры опциональны. Если параметр не задан (null),
 * API использует свои значения по умолчанию.
 *
 * @property temperature Контролирует случайность генерации (0.0 - 2.0).
 *                       Низкие значения делают ответы более детерминированными,
 *                       высокие - более креативными и разнообразными.
 * @property maxTokens Максимальное количество токенов в ответе.
 *                     Ограничивает длину генерируемого текста.
 * @property stop Список стоп-последовательностей, при встрече которых
 *                генерация прекращается. Максимум 4 последовательности.
 * @property responseFormat Формат ответа модели.
 *                          Поддерживаются: "text" (обычный текст) и "json_object" (JSON).
 */
@Serializable
data class ChatParameters(
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null
) {
    /**
     * Формат ответа от модели.
     *
     * @property type Тип формата: "text" или "json_object"
     */
    @Serializable
    data class ResponseFormat(
        val type: String
    ) {
        companion object {
            /**
             * Обычный текстовый формат ответа.
             */
            val TEXT = ResponseFormat("text")

            /**
             * JSON формат ответа. Модель будет генерировать валидный JSON.
             * Требует явного указания в промпте, что ответ должен быть в JSON.
             */
            val JSON = ResponseFormat("json_object")
        }
    }

    companion object {
        /**
         * Параметры по умолчанию (все null).
         */
        val DEFAULT = ChatParameters()
    }
}
