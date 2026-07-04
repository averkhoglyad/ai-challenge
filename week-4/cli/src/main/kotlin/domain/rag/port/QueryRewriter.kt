package io.averkhogliad.ai.challenge.week4.cli.domain.rag.port

/**
 * Порт переписывания запросов (query rewrite).
 *
 * Определяет контракт между application-слоем (SearchPipeline) и
 * infrastructure-слоем (LlmQueryRewriter).
 *
 * Принцип инверсии зависимостей (DIP): domain определяет интерфейс,
 * infrastructure его реализует.
 */
interface QueryRewriter {

    /**
     * Переписывает сложный запрос в альтернативную формулировку
     * для улучшения качества векторного поиска.
     *
     * @param query оригинальный запрос пользователя
     * @return результат переписывания с новой формулировкой и использованными токенами
     */
    suspend fun rewrite(query: String): RewriteResult
}

/**
 * Результат переписывания запроса.
 *
 * @property rewrittenQuery переписанный запрос
 * @property tokenUsage количество токенов, использованных при rewrite
 */
data class RewriteResult(
    val rewrittenQuery: String,
    val tokenUsage: Int
)
