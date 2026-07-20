package io.averkhogliad.ai.challenge.week6.application.rag

import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategy
import io.averkhogliad.ai.challenge.indexer.domain.port.VectorSearchPort
import io.averkhogliad.ai.challenge.indexer.infrastructure.extractor.DocumentExtractorRegistry
import io.averkhogliad.ai.challenge.llm.embedding.EmbeddingClient
import io.averkhogliad.ai.challenge.llm.embedding.EmbeddingRequest
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedChunkRepository

data class RagSearchResult(
    val text: String,
    val sourcePath: String,
    val startLine: Int?,
    val endLine: Int?,
    val score: Float,
)

class RagService(
    private val extractorRegistry: DocumentExtractorRegistry,
    private val chunkingStrategy: ChunkingStrategy,
    private val embeddingClient: EmbeddingClient,
    private val vectorSearch: VectorSearchPort,
    private val indexedChunkRepository: IndexedChunkRepository? = null,
) {

    suspend fun search(query: String, topK: Int = 5): List<RagSearchResult> {
        val response = embeddingClient.generate(EmbeddingRequest(listOf(query)))
        val queryEmbedding = response.embeddings.firstOrNull() ?: return emptyList()

        return vectorSearch.searchWithScores(queryEmbedding.vector, topK)
            .map { (indexedChunk, score) ->
                RagSearchResult(
                    text = indexedChunk.chunk.text,
                    sourcePath = indexedChunk.chunk.source,
                    startLine = indexedChunk.chunk.metadata["start_line"]?.toIntOrNull(),
                    endLine = indexedChunk.chunk.metadata["end_line"]?.toIntOrNull(),
                    score = score,
                )
            }
    }

    suspend fun loadIndexFromDb(projectId: String) {
        val repo = indexedChunkRepository ?: return
        vectorSearch.clear()
        val chunks = repo.findByProjectId(projectId)
        vectorSearch.addEmbeddings(chunks)
    }
}
