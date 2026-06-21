package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.ModelId
import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.model.FactCategory
import io.averkhogliad.ai.challenge.week1.domain.model.StickyFact
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort

/**
 * Извлекатель фактов из сообщений пользователя с помощью LLM.
 *
 * Анализирует сообщение и извлекает ключевые факты в формате ключ-значение.
 * Категории фактов: goal, constraint, preference, agreement, requirement.
 *
 * @property llmPort порт для взаимодействия с LLM
 */
open class FactsExtractor(
    private val llmPort: LlmPort
) {
    /**
     * Извлекает факты из сообщения пользователя.
     *
     * @param userMessage текст сообщения
     * @param messageIndex индекс сообщения в диалоге
     * @param extractionModelId опциональный ID модели для извлечения
     * @return список извлечённых фактов
     */
    open suspend fun extractFacts(
        userMessage: String,
        messageIndex: Int,
        extractionModelId: String? = null
    ): List<StickyFact> {
        val promptText = buildExtractionPrompt(userMessage)
        val prompt = Prompt(promptText)
        val config = TaskExecutionConfig(
            temperature = 0.1,
            maxTokens = 500,
            modelId = extractionModelId?.takeIf { it.isNotBlank() }?.let { ModelId(it) }
        )

        val result = llmPort.chat(prompt, config)
        val responseText = when (result) {
            is TaskResult.Success -> result.content
            is TaskResult.Partial -> result.content
            is TaskResult.Error -> return emptyList()
        }

        return parseFactsResponse(responseText, messageIndex)
    }

    private fun buildExtractionPrompt(userMessage: String): String = """
Extract key facts from the following user message. Return ONLY a JSON array of objects.
Each object must have: "category", "name", "value".

Categories: goal, constraint, preference, agreement, requirement

If no facts found, return empty array: []

User message: "$userMessage"

Example response:
[{"category":"goal","name":"api_development","value":"REST API for user management"},{"category":"requirement","name":"auth","value":"JWT authentication"}]
""".trimIndent()

    private fun parseFactsResponse(response: String, messageIndex: Int): List<StickyFact> {
        val facts = mutableListOf<StickyFact>()
        val cleaned = response.trim()

        // Извлекаем JSON-массив из ответа
        val jsonStart = cleaned.indexOf('[')
        val jsonEnd = cleaned.lastIndexOf(']')
        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) return emptyList()

        val jsonArray = cleaned.substring(jsonStart + 1, jsonEnd)

        // Простой парсинг JSON-объектов
        val objects = splitJsonObjects(jsonArray)
        for (obj in objects) {
            val category = extractJsonValue(obj, "category") ?: continue
            val name = extractJsonValue(obj, "name") ?: continue
            val value = extractJsonValue(obj, "value") ?: continue

            val factCategory = try {
                FactCategory.fromCode(category)
            } catch (_: Exception) {
                continue
            }

            facts.add(
                StickyFact(
                    key = StickyFact.createKey(factCategory, name),
                    value = value,
                    category = factCategory,
                    sourceMessageIndex = messageIndex
                )
            )
        }

        return facts
    }

    private fun splitJsonObjects(json: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1

        for (i in json.indices) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }

                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects.add(json.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }
}
