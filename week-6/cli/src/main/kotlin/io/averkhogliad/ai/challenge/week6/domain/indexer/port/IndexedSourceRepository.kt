package io.averkhogliad.ai.challenge.week6.domain.indexer.port

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexedSource

interface IndexedSourceRepository {
    suspend fun findByProjectId(projectId: String): List<IndexedSource>
    suspend fun addSource(source: IndexedSource)
    suspend fun removeSource(sourceId: String)
    suspend fun removeByProjectId(projectId: String)
}
