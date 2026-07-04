package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Режим поиска в RAG-системе.
 *
 * Sealed interface обеспечивает исчерпывающую обработку в when-выражениях.
 */
sealed interface SearchMode {
    /** Простой top-K по cosine similarity (без фильтрации) */
    data object Raw : SearchMode

    /** top-K_initial → threshold filter → top-K_final */
    data object Filtered : SearchMode

    /** top-K_initial → LLM scoring → top-K_final */
    data object Reranked : SearchMode

    /** LLM rewrite → search → filter/rerank */
    data object Rewrite : SearchMode
}
