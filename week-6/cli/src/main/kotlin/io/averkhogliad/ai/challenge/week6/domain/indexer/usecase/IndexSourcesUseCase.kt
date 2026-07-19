package io.averkhogliad.ai.challenge.week6.domain.indexer.usecase

import io.averkhogliad.ai.challenge.indexer.domain.model.Embedding
import io.averkhogliad.ai.challenge.indexer.domain.model.IndexedChunk
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategy
import io.averkhogliad.ai.challenge.indexer.domain.port.VectorSearchPort
import io.averkhogliad.ai.challenge.indexer.infrastructure.extractor.DocumentExtractorRegistry
import io.averkhogliad.ai.challenge.llm.embedding.EmbeddingClient
import io.averkhogliad.ai.challenge.llm.embedding.EmbeddingRequest
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexMetadata
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexProgress
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexedSource
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.SourceType
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexMetadataStore
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class IndexSourcesUseCase(
    private val extractorRegistry: DocumentExtractorRegistry,
    private val chunkingStrategy: ChunkingStrategy,
    private val embeddingClient: EmbeddingClient,
    private val vectorSearch: VectorSearchPort,
    private val gitPort: GitPort,
    private val metadataStore: IndexMetadataStore,
) {
    fun execute(sources: List<IndexedSource>, projectId: String, rootPath: Path): Flow<IndexProgress> = flow {
        emit(IndexProgress.Started(sources.size))

        vectorSearch.clear()
        val allFiles = collectFiles(sources)
        var totalChunks = 0

        for ((index, file) in allFiles.withIndex()) {
            try {
                val document = extractorRegistry.extract(file)
                if (document != null) {
                    val chunks = chunkingStrategy.chunk(document)
                    if (chunks.isNotEmpty()) {
                        val texts = chunks.map { it.text }
                        val response = embeddingClient.generate(EmbeddingRequest(texts))
                        val indexedChunks = chunks.zip(response.embeddings).map { (chunk, llmEmb) ->
                            IndexedChunk(
                                chunk = chunk,
                                embedding = Embedding(chunk.id, llmEmb.vector, embeddingClient.model),
                            )
                        }
                        vectorSearch.addEmbeddings(indexedChunks)
                        totalChunks += indexedChunks.size

                        emit(
                            IndexProgress.SourceComplete(
                                index = index + 1,
                                total = allFiles.size,
                                sourcePath = file.fileName.toString(),
                                chunkCount = indexedChunks.size,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                emit(IndexProgress.Error(file.toString(), e.message ?: "unknown"))
            }
        }

        val branch = gitPort.getCurrentBranch(rootPath).getOrNull()
        val commitHash = gitPort.getCurrentCommit(rootPath).getOrNull()
        metadataStore.set(
            IndexMetadata(
                projectId = projectId,
                indexedAt = Instant.now(),
                branch = branch,
                commitHash = commitHash,
                totalChunks = totalChunks,
                totalDocuments = allFiles.size,
                embeddingModel = embeddingClient.model,
            )
        )

        emit(IndexProgress.Completed(totalChunks, embeddingClient.model))
    }

    private suspend fun collectFiles(sources: List<IndexedSource>): List<Path> = withContext(Dispatchers.IO) {
        sources.flatMap { source ->
            when (source.sourceType) {
                SourceType.FILE -> listOf(source.path)
                SourceType.DIRECTORY -> {
                    if (Files.exists(source.path) && Files.isDirectory(source.path)) {
                        Files.walk(source.path).use { stream ->
                            stream
                                .filter { Files.isRegularFile(it) }
                                .filter { it.toFile().extension.lowercase() in extractorRegistry.supportedExtensions }
                                .toList()
                        }
                    } else emptyList()
                }
            }
        }
    }
}
