package io.averkhogliad.ai.challenge.week6.domain.indexer.model

sealed interface IndexProgress {
    data class Started(val totalSources: Int) : IndexProgress
    data class SourceComplete(
        val index: Int,
        val total: Int,
        val sourcePath: String,
        val chunkCount: Int,
    ) : IndexProgress

    data class Completed(val totalChunks: Int, val model: String) : IndexProgress
    data class Error(val source: String, val cause: String) : IndexProgress
}
