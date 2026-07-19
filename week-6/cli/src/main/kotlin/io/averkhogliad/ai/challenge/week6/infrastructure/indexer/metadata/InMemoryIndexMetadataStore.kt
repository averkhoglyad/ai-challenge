package io.averkhogliad.ai.challenge.week6.infrastructure.indexer.metadata

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexMetadata
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexMetadataStore
import java.util.concurrent.ConcurrentHashMap

class InMemoryIndexMetadataStore : IndexMetadataStore {
    private val store = ConcurrentHashMap<String, IndexMetadata>()

    override fun get(projectId: String): IndexMetadata? = store[projectId]

    override fun set(metadata: IndexMetadata) {
        store[metadata.projectId] = metadata
    }

    override fun clear(projectId: String) {
        store.remove(projectId)
    }
}
