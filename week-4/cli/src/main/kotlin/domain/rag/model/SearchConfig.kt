package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Иммутабельная конфигурация поиска в RAG-системе.
 *
 * @property mode режим поиска (Raw, Filtered, Reranked, Rewrite)
 * @property topKInitial количество чанков, запрашиваемых из векторного индекса
 * @property topKFinal количество чанков, оставляемых после фильтрации/реранкинга
 * @property threshold минимальный порог косинусного сходства для включения чанка в результат
 */
data class SearchConfig(
    val mode: SearchMode = SearchMode.Filtered,
    val topKInitial: Int = 50,
    val topKFinal: Int = 5,
    val threshold: Float = 0.75f
) {
    init {
        require(topKInitial > 0) { "topKInitial must be > 0, got $topKInitial" }
        require(topKFinal > 0) { "topKFinal must be > 0, got $topKFinal" }
        require(topKFinal <= topKInitial) { "topKFinal ($topKFinal) must be <= topKInitial ($topKInitial)" }
        require(threshold in 0.0f..1.0f) { "threshold must be in 0.0..1.0, got $threshold" }
    }
}
