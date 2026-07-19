package io.averkhogliad.ai.challenge.week6.application.review

import io.averkhogliad.ai.challenge.week6.domain.review.FindingCategory
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewFinding
import io.averkhogliad.ai.challenge.week6.domain.review.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object JsonReviewParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseFindings(text: String): List<ReviewFinding> {
        val cleaned = extractJson(text)
        if (cleaned.isBlank()) return emptyList()

        return try {
            val element = json.parseToJsonElement(cleaned)
            val findingsArray = when {
                element is JsonObject && element.containsKey("findings") ->
                    element["findings"]!!.jsonArray

                element is JsonObject && element.containsKey("issues") ->
                    element["issues"]!!.jsonArray

                else -> return emptyList()
            }

            findingsArray.mapNotNull { item ->
                val obj = item.jsonObject
                ReviewFinding(
                    category = parseCategory(obj["category"]?.jsonPrimitive?.content),
                    severity = parseSeverity(obj["severity"]?.jsonPrimitive?.content),
                    file = obj["file"]?.jsonPrimitive?.content,
                    line = obj["line"]?.jsonPrimitive?.content?.toIntOrNull(),
                    description = obj["description"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    recommendation = obj["recommendation"]?.jsonPrimitive?.content,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseSummary(text: String): String? {
        val cleaned = extractJson(text)
        if (cleaned.isBlank()) return null

        return try {
            val element = json.parseToJsonElement(cleaned)
            when (element) {
                is JsonObject -> element["summary"]?.jsonPrimitive?.content
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJson(text: String): String {
        // Remove markdown code fences
        var result = text
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        // Find first { and last }
        val start = result.indexOf('{')
        val end = result.lastIndexOf('}')
        if (start >= 0 && end > start) {
            result = result.substring(start, end + 1)
        }

        return result
    }

    internal fun parseCategory(raw: String?): FindingCategory {
        if (raw == null) return FindingCategory.OTHER
        return try {
            FindingCategory.valueOf(raw.uppercase())
        } catch (_: Exception) {
            when {
                raw.contains("bug", ignoreCase = true) -> FindingCategory.BUG
                raw.contains("arch", ignoreCase = true) -> FindingCategory.ARCHITECTURE
                raw.contains("perform", ignoreCase = true) -> FindingCategory.PERFORMANCE
                raw.contains("secur", ignoreCase = true) -> FindingCategory.SECURITY
                raw.contains("best", ignoreCase = true) -> FindingCategory.BEST_PRACTICE
                raw.contains("read", ignoreCase = true) -> FindingCategory.READABILITY
                raw.contains("maintain", ignoreCase = true) -> FindingCategory.MAINTAINABILITY
                else -> FindingCategory.OTHER
            }
        }
    }

    internal fun parseSeverity(raw: String?): Severity {
        if (raw == null) return Severity.INFO
        return try {
            Severity.valueOf(raw.uppercase())
        } catch (_: Exception) {
            when {
                raw.contains("crit", ignoreCase = true) -> Severity.CRITICAL
                raw.contains("warn", ignoreCase = true) -> Severity.WARNING
                else -> Severity.INFO
            }
        }
    }
}
