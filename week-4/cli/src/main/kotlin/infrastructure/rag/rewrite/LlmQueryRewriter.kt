package io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rewrite

import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.QueryRewriter
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RewriteResult
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort

/**
 * LLM-адаптер [QueryRewriter]: переписывает сложный запрос в альтернативную формулировку.
 *
 * Использует LLM для генерации более конкретного запроса с ключевыми словами
 * для улучшения качества векторного поиска.
 *
 * При ошибке LLM — fallback на оригинальный запрос с [tokenUsage] = 0.
 */
class LlmQueryRewriter(
    private val llm: LlmPort,
    private val tokenEstimateCharsPerToken: Int = 4
) : QueryRewriter {

    override suspend fun rewrite(query: String): RewriteResult {
        return try {
            val prompt = """
                Переформулируй следующий запрос для улучшения поиска в базе знаний.
                Сделай его более конкретным и добавь ключевые слова.
                
                Оригинальный запрос: $query
                
                Ответь только переформулированным запросом, без пояснений.
            """.trimIndent()

            val response = llm.chat(Prompt(prompt), TaskExecutionConfig())
            val content = when (response) {
                is TaskResult.Success -> response.content
                is TaskResult.Error -> throw RuntimeException(response.message)
                is TaskResult.Partial -> response.content
            }

            RewriteResult(
                rewrittenQuery = content.trim(),
                tokenUsage = prompt.length / tokenEstimateCharsPerToken
            )
        } catch (e: Exception) {
            System.err.println("[WARN] Query rewrite failed: ${e.message}, using original query")
            RewriteResult(rewrittenQuery = query, tokenUsage = 0)
        }
    }
}
