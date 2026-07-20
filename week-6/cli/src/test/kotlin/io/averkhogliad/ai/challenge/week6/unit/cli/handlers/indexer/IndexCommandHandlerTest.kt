package io.averkhogliad.ai.challenge.week6.unit.cli.handlers.indexer

import io.averkhogliad.ai.challenge.week6.application.StartupIndexingProgress
import io.averkhogliad.ai.challenge.week6.application.StartupIndexingUseCase
import io.averkhogliad.ai.challenge.week6.cli.handlers.indexer.IndexCommandHandler
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.cli.repl.core.CommandEffect
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import java.time.Instant

class IndexCommandHandlerTest : FreeSpec({

    val project = Project(
        id = "project-id",
        name = "project",
        rootPath = Path.of("."),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    "canHandle returns true only for /index" {
        val handler = IndexCommandHandler(mockk(), { project })

        handler.canHandle("/index") shouldBe true
        handler.canHandle("/index now") shouldBe false
        handler.canHandle("/other") shouldBe false
    }

    "returns an error when there is no active project" {
        runTest {
            val handler = IndexCommandHandler(mockk(), { null })

            val result = handler.execute("/index")

            (result as CommandEffect.Print).isError shouldBe true
            result.message shouldContain "Нет активного проекта"
        }
    }

    "streams indexing progress" {
        runTest {
            val useCase = mockk<StartupIndexingUseCase>()
            every { useCase.execute(project) } returns flowOf(
                StartupIndexingProgress.IndexingStarted,
                StartupIndexingProgress.Completed(3, "test-model"),
            )
            val handler = IndexCommandHandler(useCase, { project })

            val result = handler.execute("/index")

            val messages = (result as CommandEffect.StreamOutput).contentFlow.toList()
            messages shouldBe listOf(
                "Индексация документации...",
                "Индекс готов: 3 чанков, модель: test-model",
            )
        }
    }
})
