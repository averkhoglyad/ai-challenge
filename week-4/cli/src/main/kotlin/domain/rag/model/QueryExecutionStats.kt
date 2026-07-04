package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

import java.time.Instant
import java.util.*

// ═══════════════════════════════════════════════════════════════════
// Статистика выполнения запроса (5 ключевых метрик)
// ═══════════════════════════════════════════════════════════════════

/**
 * Статистика выполнения одного RAG-запроса.
 *
 * Содержит 5 ключевых метрик согласно спецификации Task 3:
 * 1. totalMs — общее время запроса
 * 2. ChunkFlow — воронка чанков (initial → filtered → final)
 * 3. ScoreDelta — средний score до и после фильтрации
 * 4. TokenBreakdown — токены по этапам (rewrite/rerank/answer)
 * 5. DropBreakdown — отброшенные чанки с причинами
 *
 * @property queryId уникальный идентификатор запроса
 * @property timestamp время выполнения запроса
 * @property mode режим поиска, в котором выполнялся запрос
 * @property totalMs общее время выполнения (метрика 1)
 * @property chunks воронка чанков (метрика 2)
 * @property score дельта score (метрика 3)
 * @property tokens разбивка токенов (метрика 4)
 * @property dropped разбивка причин отсева (метрика 5)
 */
data class QueryExecutionStats(
    val queryId: UUID,
    val timestamp: Instant,
    val mode: SearchMode,
    val totalMs: Long,
    val chunks: ChunkFlow,
    val score: ScoreDelta,
    val tokens: TokenBreakdown,
    val dropped: DropBreakdown
)

// ═══════════════════════════════════════════════════════════════════
// Метрика 2: Воронка чанков
// ═══════════════════════════════════════════════════════════════════

/**
 * Воронка чанков: отслеживает количество чанков на каждом этапе pipeline.
 *
 * @property initial количество чанков из векторного поиска (topK_initial)
 * @property filtered количество чанков после threshold/rerank
 * @property final количество чанков после взятия topK_final
 */
data class ChunkFlow(
    val initial: Int,
    val filtered: Int,
    val final: Int
)

// ═══════════════════════════════════════════════════════════════════
// Метрика 3: Дельта score
// ═══════════════════════════════════════════════════════════════════

/**
 * Изменение среднего score чанков до и после фильтрации.
 *
 * @property initialAvg средний score до фильтрации
 * @property filteredAvg средний score после фильтрации
 */
data class ScoreDelta(
    val initialAvg: Float,
    val filteredAvg: Float
)

// ═══════════════════════════════════════════════════════════════════
// Метрика 4: Разбивка токенов
// ═══════════════════════════════════════════════════════════════════

/**
 * Разбивка использованных токенов по этапам pipeline.
 *
 * @property rewrite токены, использованные при rewrite запроса (null если rewrite не выполнялся)
 * @property rerank токены, использованные при rerank (null если rerank не выполнялся)
 * @property answer токены, использованные при генерации финального ответа
 */
data class TokenBreakdown(
    val rewrite: Int?,
    val rerank: Int?,
    val answer: Int
) {
    /** Общее количество токенов по всем этапам */
    val total: Int get() = (rewrite ?: 0) + (rerank ?: 0) + answer
}

// ═══════════════════════════════════════════════════════════════════
// Метрика 5: Разбивка причин отсева
// ═══════════════════════════════════════════════════════════════════

/**
 * Разбивка отброшенных чанков по причинам.
 *
 * @property byThreshold количество чанков, отброшенных threshold-фильтром
 * @property byTopK количество чанков, отброшенных из-за лимита topK_final
 * @property byRerank количество чанков, отброшенных LLM-реранкером (низкий score)
 */
data class DropBreakdown(
    val byThreshold: Int,
    val byTopK: Int,
    val byRerank: Int
)
