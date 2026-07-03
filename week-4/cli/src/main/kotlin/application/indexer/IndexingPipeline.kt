package io.averkhogliad.ai.challenge.week4.cli.application.indexer

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.EmbeddingProviderConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.IndexerConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.ChunkingStrategy
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.DocumentExtractor
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.EmbeddingGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.UUID

/**
 * Оркестратор пайплайна индексации.
 *
 * Принимает [Flow]<[Document]>, пропускает через цепочку:
 * extract → chunk → embed → save,
 * и управляет жизненным циклом [IndexingRun].
 */
class IndexingPipeline(
    private val extractors: List<DocumentExtractor>,
    private val chunkingStrategy: ChunkingStrategy,
    private val embeddingGenerator: EmbeddingGenerator,
    private val repository: IndexRepository,
    private val config: IndexerConfig
) {

    /**
     * Запускает пайплайн индексации.
     *
     * @param documents поток документов из [DocumentLoader]
     * @param sourcePath путь к исходной директории (для метаданных run)
     * @param strategy опциональное переопределение стратегии чанкинга
     * @return ID созданного run
     * @throws IllegalStateException если сервис эмбеддингов недоступен
     */
    suspend fun execute(
        documents: Flow<Document>,
        sourcePath: String,
        strategy: ChunkingStrategy? = null
    ): UUID {
        val effectiveStrategy = strategy ?: chunkingStrategy

        if (!embeddingGenerator.healthCheck()) {
            throw IllegalStateException(
                "Embedding service is not available. Please check your configuration."
            )
        }

        val modelName = when (val pc = config.embedding.providerConfig) {
            is EmbeddingProviderConfig.Ollama -> pc.model
            is EmbeddingProviderConfig.OpenAi -> pc.model
        }

        val isFixedSize = effectiveStrategy.type == ChunkingStrategyType.FIXED_SIZE

        val runId = UUID.randomUUID()
        val run = IndexingRun(
            id = runId,
            startedAt = Instant.now(),
            finishedAt = null,
            strategy = effectiveStrategy.type,
            sourcePath = sourcePath,
            chunkSize = if (isFixedSize) config.chunkSize else null,
            overlap = if (isFixedSize) config.overlap else null,
            embeddingModel = modelName,
            status = RunStatus.RUNNING,
            totalChunks = 0,
            errorMessage = null,
            metadata = emptyMap()
        )
        repository.createRun(run)

        try {
            var totalChunks = 0
            val batchSize = config.embedding.batchSize

            documents
                .mapNotNull { doc -> extractors.find { it.canHandle(doc.type) }?.extract(doc) }
                .flatMapConcat { extracted ->
                    effectiveStrategy.chunk(extracted, runId).toList().asFlow()
                }
                .chunked(batchSize)
                .collect { batch ->
                    val texts = batch.map { it.id to it.text }
                    val embeddings = embeddingGenerator.generateBatch(texts)
                    val indexedChunks = batch.zip(embeddings).map { (chunk, emb) ->
                        IndexedChunk(chunk, emb)
                    }
                    repository.saveBatch(indexedChunks)
                    totalChunks += indexedChunks.size
                }

            repository.updateRunStatus(runId, RunStatus.COMPLETED, totalChunks)
            repository.setActiveIndex(runId)

            return runId
        } catch (e: Exception) {
            repository.updateRunStatus(runId, RunStatus.FAILED, errorMessage = e.message)
            throw e
        }
    }

    private fun <T> Iterable<T>.asFlow(): Flow<T> = flow {
        forEach { emit(it) }
    }
}
