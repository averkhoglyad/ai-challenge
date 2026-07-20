package io.averkhogliad.ai.challenge.week6.unit.infrastructure.tools

import io.averkhogliad.ai.challenge.indexer.domain.model.Chunk
import io.averkhogliad.ai.challenge.indexer.domain.model.Embedding
import io.averkhogliad.ai.challenge.indexer.domain.model.IndexedChunk
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategy
import io.averkhogliad.ai.challenge.indexer.domain.port.VectorSearchPort
import io.averkhogliad.ai.challenge.indexer.infrastructure.extractor.DocumentExtractorRegistry
import io.averkhogliad.ai.challenge.llm.embedding.EmbeddingClient
import io.averkhogliad.ai.challenge.llm.embedding.EmbeddingResponse
import io.averkhogliad.ai.challenge.llm.embedding.LlmEmbedding
import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.rag.RagService
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.indexer.model.StalenessResult
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.CheckIndexStalenessUseCase
import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import io.averkhogliad.ai.challenge.week6.infrastructure.tools.RagSearchBuiltinTool
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Path
import java.util.*

class RagSearchBuiltinToolTest : FreeSpec({

    fun queryArguments(query: String) = buildJsonObject {
        put("query", JsonPrimitive(query))
    }

    "returns warning when index is absent" {
        runTest {
            val ragService = mockk<RagService>()
            val stalenessUseCase = mockk<CheckIndexStalenessUseCase>()
            val contextProvider = mockk<ProjectContextProvider>()
            coEvery { contextProvider.getContext() } returns DomainResult.Success(
                ProjectContext("project-id", Path.of("."), emptyList(), isGitEnabled = false)
            )
            coEvery { stalenessUseCase.execute(any(), any()) } returns StalenessResult.NoIndex
            val tool = RagSearchBuiltinTool(ragService, stalenessUseCase, contextProvider)

            val result = tool.execute(queryArguments("architecture"))

            require(result is ToolResult.Success)
            result.content shouldContain "Индекс отсутствует"
        }
    }

    "returns warning when index staleness check fails" {
        runTest {
            val ragService = mockk<RagService>()
            val stalenessUseCase = mockk<CheckIndexStalenessUseCase>()
            val contextProvider = mockk<ProjectContextProvider>()
            coEvery { contextProvider.getContext() } returns DomainResult.Success(
                ProjectContext("project-id", Path.of("."), emptyList(), isGitEnabled = false)
            )
            coEvery { stalenessUseCase.execute(any(), any()) } throws IllegalStateException("Git unavailable")
            val tool = RagSearchBuiltinTool(ragService, stalenessUseCase, contextProvider)

            val result = tool.execute(queryArguments("architecture"))

            require(result is ToolResult.Success)
            result.content shouldContain "RAG unavailable"
            result.content shouldContain "Git unavailable"
        }
    }

    "includes source path and line range in search result" {
        runTest {
            val embeddingClient = mockk<EmbeddingClient>()
            val vectorSearch = mockk<VectorSearchPort>()
            val chunk = Chunk(
                id = UUID.randomUUID(),
                text = "Architecture details",
                source = "docs/architecture.md",
                metadata = mapOf("start_line" to "10", "end_line" to "14"),
            )
            val indexedChunk = IndexedChunk(
                chunk = chunk,
                embedding = Embedding(chunk.id, floatArrayOf(0.1f), "test"),
            )
            coEvery { embeddingClient.generate(any()) } returns EmbeddingResponse(
                embeddings = listOf(LlmEmbedding("architecture", floatArrayOf(0.1f), 0)),
                model = "test",
            )
            coEvery { vectorSearch.searchWithScores(any(), any()) } returns listOf(indexedChunk to 0.9f)
            val ragService = RagService(
                extractorRegistry = mockk<DocumentExtractorRegistry>(),
                chunkingStrategy = mockk<ChunkingStrategy>(),
                embeddingClient = embeddingClient,
                vectorSearch = vectorSearch,
            )
            val tool = RagSearchBuiltinTool(ragService)

            val result = tool.execute(queryArguments("architecture"))

            require(result is ToolResult.Success)
            result.content shouldContain "docs/architecture.md:10-14"
            result.content shouldContain "Architecture details"
        }
    }

    "returns warning when RAG search fails" {
        runTest {
            val embeddingClient = mockk<EmbeddingClient>()
            coEvery { embeddingClient.generate(any()) } throws IllegalStateException("embedding service unavailable")
            val ragService = RagService(
                extractorRegistry = mockk<DocumentExtractorRegistry>(),
                chunkingStrategy = mockk<ChunkingStrategy>(),
                embeddingClient = embeddingClient,
                vectorSearch = mockk<VectorSearchPort>(),
            )
            val tool = RagSearchBuiltinTool(ragService)

            val result = tool.execute(queryArguments("architecture"))

            require(result is ToolResult.Success)
            result.content shouldContain "RAG unavailable"
            result.content shouldContain "embedding service unavailable"
        }
    }
})
