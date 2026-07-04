package io.averkhogliad.ai.challenge.week4.cli.unit.cli.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.MetricsAnalyzer
import io.averkhogliad.ai.challenge.week4.cli.application.rag.QueryHistoryService
import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagConfigService
import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagStateManager
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.*
import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ChunkingStrategyType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexingRun
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.RunStatus
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.util.*

class RagCommandHandlerTest : FreeSpec({

    val runId = UUID.randomUUID()
    lateinit var repository: IndexRepository
    lateinit var renderer: RagCommandRenderer
    lateinit var configService: RagConfigService
    lateinit var historyService: QueryHistoryService
    lateinit var metricsAnalyzer: MetricsAnalyzer
    lateinit var historyRenderer: QueryHistoryRenderer
    lateinit var analysisRenderer: MetricsAnalysisRenderer
    lateinit var ragStateManager: RagStateManager
    lateinit var ragConfig: RagConfig
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
        configService = mockk()
        historyService = mockk()
        metricsAnalyzer = mockk()
        historyRenderer = mockk(relaxed = true)
        analysisRenderer = mockk(relaxed = true)
        ragStateManager = mockk(relaxed = true)
        ragConfig = RagConfig(relevanceThreshold = 0.70f)
        handler = RagCommandHandler(
            repository,
            renderer,
            configService,
            historyService,
            metricsAnalyzer,
            historyRenderer,
            analysisRenderer,
            ragStateManager = ragStateManager,
            ragConfig = ragConfig
        )
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

    // ──── Task 3: Mode, Threshold, TopK, Config commands ────

    "handle SetMode" - {

        "updates ragState config mode" {
            // given
            val state = CliState(ragState = RagSessionState())
            val newRagState = state.ragState.copy(
                config = state.ragState.config.copy(mode = SearchMode.Reranked)
            )
            coEvery { configService.setMode(state.ragState, SearchMode.Reranked) } returns newRagState

            // when
            val newState = handler.handle(RagCommand.SetMode(SearchMode.Reranked), state)

            // then
            newState.ragState.config.mode shouldBe SearchMode.Reranked
        }
    }

    "handle SetThreshold" - {

        "updates ragState config threshold" {
            // given
            val state = CliState(ragState = RagSessionState())
            val newRagState = state.ragState.copy(
                config = state.ragState.config.copy(threshold = 0.9f)
            )
            coEvery { configService.setThreshold(state.ragState, 0.9f) } returns newRagState

            // when
            val newState = handler.handle(RagCommand.SetThreshold(0.9f), state)

            // then
            newState.ragState.config.threshold shouldBe 0.9f
        }
    }

    "handle SetTopK" - {

        "updates ragState config topK" {
            // given
            val state = CliState(ragState = RagSessionState())
            val newRagState = state.ragState.copy(
                config = state.ragState.config.copy(topKInitial = 100, topKFinal = 10)
            )
            coEvery { configService.setTopK(state.ragState, 100, 10) } returns newRagState

            // when
            val newState = handler.handle(RagCommand.SetTopK(100, 10), state)

            // then
            newState.ragState.config.topKInitial shouldBe 100
            newState.ragState.config.topKFinal shouldBe 10
        }
    }

    "handle Config" - {

        "does not change state" {
            // given
            val state = CliState(ragState = RagSessionState())

            // when
            val newState = handler.handle(RagCommand.Config, state)

            // then
            newState shouldBe state
        }
    }

    // ──── Task 3: History and analytics commands ────

    "handle History calls historyService.getLast" {
        // given
        val state = CliState()
        coEvery { historyService.getLast(5) } returns emptyList()

        // when
        val newState = handler.handle(RagCommand.History(limit = 5), state)

        // then
        coVerify { historyService.getLast(5) }
        newState shouldBe state
    }

    "handle HistoryDetail calls historyService.getDetailed" {
        // given
        val state = CliState()
        coEvery { historyService.getDetailed(1L) } returns null

        // when
        val newState = handler.handle(RagCommand.HistoryDetail(1), state)

        // then
        coVerify { historyService.getDetailed(1L) }
        newState shouldBe state
    }

    "handle Analyze calls metricsAnalyzer.analyze" {
        // given
        val state = CliState()
        coEvery { historyService.getAggregatedStats(50) } returns mockk()
        coEvery { metricsAnalyzer.analyze() } returns mockk()

        // when
        val newState = handler.handle(RagCommand.Analyze, state)

        // then
        coVerify { metricsAnalyzer.analyze() }
        newState shouldBe state
    }

    // ──── Task 4: Anti-hallucination commands ────

    "handle SetRelevanceThreshold" - {

        "updates threshold and shows info" {
            val state = CliState(ragState = RagSessionState(relevanceThreshold = 0.70f))
            coEvery { ragStateManager.getState() } returns state.ragState
            coEvery { ragStateManager.getState() } returns state.ragState.copy(relevanceThreshold = 0.85f)

            val newState = handler.handle(RagCommand.SetRelevanceThreshold(0.85f), state)

            newState.ragState.relevanceThreshold shouldBe 0.85f
        }
    }

    "handle ResetSettings" - {

        "resets to config default" {
            val state = CliState(ragState = RagSessionState(relevanceThreshold = 0.85f))
            coEvery { ragStateManager.getState() } returns state.ragState
            coEvery { ragStateManager.getState() } returns RagSessionState(
                config = state.ragState.config,
                relevanceThreshold = 0.70f
            )

            val newState = handler.handle(RagCommand.ResetSettings, state)

            newState.ragState.relevanceThreshold shouldBe 0.70f
        }
    }
})
