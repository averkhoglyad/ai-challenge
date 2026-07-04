package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Иммутабельное состояние RAG-сессии.
 *
 * Хранит настройки Retrieval-Augmented Generation, действующие в рамках CLI-сессии.
 * Изменения выполняются только через [copy].
 *
 * @property enabled включён ли режим RAG
 * @property topK количество релевантных чанков, добавляемых в контекст запроса
 * @property similarityThreshold минимальный порог косинусного сходства для включения чанка в контекст
 */
data class RagSessionState(
    val enabled: Boolean = false,
    val topK: Int = 5,
    val similarityThreshold: Float = 0.7f,
    val config: SearchConfig = SearchConfig()
)
