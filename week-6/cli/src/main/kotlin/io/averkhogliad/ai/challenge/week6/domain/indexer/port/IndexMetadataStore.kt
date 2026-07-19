package io.averkhogliad.ai.challenge.week6.domain.indexer.port

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexMetadata

interface IndexMetadataStore {
    fun get(projectId: String): IndexMetadata?
    fun set(metadata: IndexMetadata)
    fun clear(projectId: String)
}
