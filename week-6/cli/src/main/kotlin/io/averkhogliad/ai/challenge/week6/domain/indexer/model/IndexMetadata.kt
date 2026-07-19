package io.averkhogliad.ai.challenge.week6.domain.indexer.model

import java.time.Instant

data class IndexMetadata(
    val projectId: String,
    val indexedAt: Instant,
    val branch: String?,
    val commitHash: String?,
    val totalChunks: Int,
    val totalDocuments: Int,
    val embeddingModel: String,
)
