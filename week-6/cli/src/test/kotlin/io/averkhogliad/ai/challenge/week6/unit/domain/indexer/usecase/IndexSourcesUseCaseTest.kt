package io.averkhogliad.ai.challenge.week6.unit.domain.indexer.usecase

import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategy
import io.averkhogliad.ai.challenge.indexer.domain.port.VectorSearchPort
import io.averkhogliad.ai.challenge.indexer.infrastructure.extractor.DocumentExtractorRegistry
import io.averkhogliad.ai.challenge.llm.embedding.EmbeddingClient
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexMetadataStore
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedChunkRepository
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.IndexSourcesUseCase
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.kotest.core.spec.style.FreeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Path

class IndexSourcesUseCaseTest : FreeSpec({

    "saves rebuilt chunks through the repository replacement operation" {
        runTest {
            val vectorSearch = mockk<VectorSearchPort>()
            val gitPort = mockk<GitPort>()
            val metadataStore = mockk<IndexMetadataStore>()
            val chunkRepository = mockk<IndexedChunkRepository>()
            val embeddingClient = mockk<EmbeddingClient>()
            coEvery { vectorSearch.clear() } returns Unit
            coEvery { chunkRepository.save("project-id", emptyList()) } returns Unit
            coEvery { gitPort.getCurrentBranch(any()) } returns DomainResult.Success("main")
            coEvery { gitPort.getCurrentCommit(any()) } returns DomainResult.Success("commit")
            io.mockk.every { embeddingClient.model } returns "test-model"
            io.mockk.every { metadataStore.set(any()) } returns Unit
            val useCase = IndexSourcesUseCase(
                extractorRegistry = mockk<DocumentExtractorRegistry>(),
                chunkingStrategy = mockk<ChunkingStrategy>(),
                embeddingClient = embeddingClient,
                vectorSearch = vectorSearch,
                gitPort = gitPort,
                metadataStore = metadataStore,
                indexedChunkRepository = chunkRepository,
            )

            useCase.execute(emptyList(), "project-id", Path.of(".")).toList()

            coVerify(exactly = 1) { chunkRepository.save("project-id", emptyList()) }
            coVerify(exactly = 0) { chunkRepository.deleteByProjectId(any()) }
        }
    }
})
