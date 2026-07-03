package io.averkhogliad.ai.challenge.week4.cli.infrastructure.preset

import io.averkhogliad.ai.challenge.week4.cli.domain.model.PromptPreset
import io.averkhogliad.ai.challenge.week4.cli.domain.model.PromptSource

/**
 * Ручной парсинг YAML front-matter из markdown-файлов пресетов.
 *
 * Формат front-matter:
 * ```
 * ---
 * name: smart-task-planner
 * description: |
 *   Описание пресета...
 * tags:
 *   - planning
 *   - weather
 * ---
 * ```
 *
 * ## Архитектурная роль
 * - **Functional Core** — чистая функция без I/O
 */
object FrontMatterParser {

    private val FRONT_MATTER_REGEX =
        Regex("^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n(.*)\$", RegexOption.DOT_MATCHES_ALL)

    /**
     * Парсит содержимое markdown-файла с YAML front-matter.
     *
     * @param content полное содержимое файла
     * @return [PromptPreset] или null, если front-matter не найден или повреждён
     */
    fun parse(content: String, source: PromptSource = PromptSource.BUILTIN): PromptPreset? {
        val match = FRONT_MATTER_REGEX.find(content) ?: return null

        val yamlBlock = match.groupValues[1]
        val instruction = match.groupValues[2].trim()

        val fields = parseYamlFields(yamlBlock)
        val name = fields["name"] ?: return null
        val description = fields["description"] ?: return null
        val tags = parseTags(fields["tags"])

        return PromptPreset(
            name = name,
            description = description,
            instruction = instruction,
            source = source,
            tags = tags
        )
    }

    /**
     * Парсит простые YAML-поля (key: value) из блока front-matter.
     * Поддерживает многострочные значения через `|`.
     */
    private fun parseYamlFields(yamlBlock: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var currentKey: String? = null
        val multilineBuffer = StringBuilder()

        for (rawLine in yamlBlock.lines()) {
            val line = rawLine.trimEnd()

            // Многострочное значение (с отступом)
            if (currentKey != null && (line.startsWith("  ") || line.startsWith("\t"))) {
                if (multilineBuffer.isNotEmpty()) multilineBuffer.append("\n")
                multilineBuffer.append(line.trimStart())
                continue
            }

            // Сохраняем предыдущее многострочное значение
            if (currentKey != null) {
                result[currentKey] = multilineBuffer.toString().trim()
                currentKey = null
                multilineBuffer.clear()
            }

            // Ищем key: value
            val colonIndex = line.indexOf(':')
            if (colonIndex < 0) continue

            val key = line.substring(0, colonIndex).trim()
            val rest = line.substring(colonIndex + 1).trim()

            when {
                rest == "|" || rest == "|-" || rest == ">" || rest == ">-" -> {
                    // Многострочное значение
                    currentKey = key
                }

                rest.startsWith("|") -> {
                    // | с текстом на той же строке
                    val value = rest.removePrefix("|").trim()
                    currentKey = key
                    if (value.isNotEmpty()) multilineBuffer.append(value)
                }

                rest.isNotEmpty() -> {
                    result[key] = rest
                }
            }
        }

        // Сохраняем последнее многострочное значение
        if (currentKey != null) {
            result[currentKey] = multilineBuffer.toString().trim()
        }

        return result
    }

    /**
     * Парсит список тегов из YAML-значения.
     * Поддерживает форматы:
     * - `[planning, weather]`
     * - `planning, weather`
     * - список с `- ` префиксом
     */
    private fun parseTags(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .lines()
            .flatMap { line ->
                val trimmed = line.trim().removePrefix("-").trim()
                if (trimmed.isBlank()) emptyList()
                else trimmed.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"", "'") }
                    .filter { it.isNotBlank() }
            }
            .filter { it.isNotBlank() }
    }
}
