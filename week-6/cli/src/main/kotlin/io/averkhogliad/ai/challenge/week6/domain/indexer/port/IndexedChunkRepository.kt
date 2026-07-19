package io.averkhogliad.ai.challenge.week6.domain.indexer.port

import io.averkhogliad.ai.challenge.indexer.domain.model.IndexedChunk

interface IndexedChunkRepository {
    suspend fun save(projectId: String, chunks: List<IndexedChunk>)
    suspend fun findByProjectId(projectId: String): List<IndexedChunk>
    suspend fun deleteByProjectId(projectId: String)
}
