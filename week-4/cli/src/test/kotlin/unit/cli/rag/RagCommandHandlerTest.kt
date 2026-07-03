package io.averkhogliad.ai.challenge.week4.cli.unit.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommand
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexingRun
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.RunStatus
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.util.*

class RagCommandHandlerTest : FreeSpec({

    val runId = UUID.randomUUID()
    lateinit var repository: IndexRepository
    lateinit var renderer: RagCommandRenderer
    lateinit var handler: RagCommandHandler

    fun completedRun(): IndexingRun = IndexingRun(
        id = runId,
        startedAt = Instant.now(),
        finishedAt = Instant.now(),
        strategy = ChunkingStrategyType.STRUCTURAL,
        sourcePath = "./docs",
        chunkSize = null,
        overlap = null,
        embeddingModel = "test-model",
        status = RunStatus.COMPLETED,
        totalChunks = 23,
        errorMessage = null,
        metadata = emptyMap()
    )

    beforeEach {
        repository = mockk()
        renderer = mockk(relaxed = true)
        handler = RagCommandHandler(repository, renderer)
    }

    "handle Toggle" - {

        "enables RAG when disabled and active index exists" {
            // given
            val state = CliState(ragState = RagSessionState(enabled = false))
            coEvery { repository.getActiveIndex() } returns runId
            coEvery { repository.getRun(runId) } returns completedRun()

            // when
            val newState = handler.handle(RagCommand.Toggle, state)

            // then
            newState.ragState.enabled shouldBe true
        }

        "enables RAG when disabled and no active index (toggle always works)" {
            // given
            val state = CliState(ragState = RagSessionState(enabled = false))
            coEvery { repository.getActiveIndex() } returns null

            // when
            val newState = handler.handle(RagCommand.Toggle, state)

            // then
            newState.ragState.enabled shouldBe true
        }

        "disables RAG when enabled" {
            // given
            val state = CliState(ragState = RagSessionState(enabled = true))
            coEvery { repository.getActiveIndex() } returns runId
            coEvery { repository.getRun(runId) } returns completedRun()

            // when
            val newState = handler.handle(RagCommand.Toggle, state)

            // then
            newState.ragState.enabled shouldBe false
        }
    }

    "handle Status" - {

        "shows status with active index" {
            // given
            val state = CliState(ragState = RagSessionState(enabled = true, topK = 5, similarityThreshold = 0.7f))
            coEvery { repository.getActiveIndex() } returns runId
            coEvery { repository.getRun(runId) } returns completedRun()

            // when
            val newState = handler.handle(RagCommand.Status, state)

            // then — state unchanged
            newState shouldBe state
        }

        "shows status without active index" {
            // given
            val state = CliState(ragState = RagSessionState(enabled = true))
            coEvery { repository.getActiveIndex() } returns null

            // when
            val newState = handler.handle(RagCommand.Status, state)

            // then — state unchanged
            newState shouldBe state
        }
    }

    "handle List" - {

        "shows completed runs when available" {
            // given
            val state = CliState()
            val runs = listOf(completedRun())
            coEvery { repository.getAllRuns() } returns runs
            coEvery { repository.getActiveIndex() } returns runId

            // when
            val newState = handler.handle(RagCommand.List, state)

            // then — state unchanged
            newState shouldBe state
        }

        "shows empty message when no completed runs" {
            // given
            val state = CliState()
            coEvery { repository.getAllRuns() } returns emptyList()
            coEvery { repository.getActiveIndex() } returns null

            // when
            val newState = handler.handle(RagCommand.List, state)

            // then — state unchanged
            newState shouldBe state
        }

        "filters out non-completed runs" {
            // given
            val state = CliState()
            val runningRun = completedRun().copy(
                id = UUID.randomUUID(),
                status = RunStatus.RUNNING
            )
            val failedRun = completedRun().copy(
                id = UUID.randomUUID(),
                status = RunStatus.FAILED
            )
            val completed = completedRun()
            val runs = listOf(runningRun, failedRun, completed)
            coEvery { repository.getAllRuns() } returns runs
            coEvery { repository.getActiveIndex() } returns runId

            // when
            val newState = handler.handle(RagCommand.List, state)

            // then — state unchanged (renderer должен показать только completed)
            newState shouldBe state
        }
    }
})
