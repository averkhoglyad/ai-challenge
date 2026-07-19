package io.averkhogliad.ai.challenge.week6.unit.cli.handlers.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.FileSearchUseCase
import io.averkhogliad.ai.challenge.week6.cli.handlers.fileops.FindCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.rendering.SearchResultsRenderer
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.SearchHit
import io.averkhogliad.cli.repl.core.CommandEffect
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class FindCommandHandlerTest : FreeSpec({

    lateinit var useCase: FileSearchUseCase
    lateinit var renderer: SearchResultsRenderer
    lateinit var handler: FindCommandHandler
    lateinit var rootPath: Path

    beforeEach {
        useCase = mockk()
        renderer = mockk()
        handler = FindCommandHandler(useCase, renderer)
        rootPath = createTempDirectory("find-test-")
    }

    afterEach {
        rootPath.toFile().deleteRecursively()
    }

    "canHandle" - {

        "returns true for /find" {
            handler.canHandle("/find") shouldBe true
        }

        "returns true for /find with query" {
            handler.canHandle("/find some query") shouldBe true
        }

        "returns true for /search alias" {
            handler.canHandle("/search") shouldBe true
        }

        "returns true for /search with query" {
            handler.canHandle("/search query") shouldBe true
        }

        "returns false for other commands" {
            handler.canHandle("/other") shouldBe false
        }
    }

    "execute" - {

        "parses first arg as query and calls use case" {
            runTest {
                coEvery { useCase.execute(any(), any(), any(), any()) } returns
                        DomainResult.Success(emptyList())
                every { renderer.render(emptyList()) } returns ""

                handler.execute("/find test")

                coVerify(exactly = 1) { useCase.execute(any(), any(), any(), any()) }
            }
        }

        "renders results on success" {
            runTest {
                val relPath = RelativePath.from("src/App.kt", rootPath).getOrThrow()
                val hits = listOf(
                    SearchHit(path = relPath, line = 10, snippet = "found something")
                )
                coEvery { useCase.execute(any(), any(), any(), any()) } returns
                        DomainResult.Success(hits)
                every { renderer.render(hits) } returns "rendered table"

                val result = handler.execute("/find test")

                (result as CommandEffect.Print).message shouldContain "Результаты поиска"
                (result as CommandEffect.Print).message shouldContain "1 совпадений"
                (result as CommandEffect.Print).message shouldContain "rendered table"
            }
        }

        "displays nothing found when empty results" {
            runTest {
                coEvery { useCase.execute(any(), any(), any(), any()) } returns
                        DomainResult.Success(emptyList())

                val result = handler.execute("/find nonexistent")

                (result as CommandEffect.Print).message shouldContain "Ничего не найдено"
            }
        }

        "displays error when use case fails" {
            runTest {
                coEvery { useCase.execute(any(), any(), any(), any()) } returns
                        DomainResult.Failure(DomainError.NoActiveProject())

                val result = handler.execute("/find bad")

                (result as CommandEffect.DisplayDomainError<*>).error.message shouldContain "Активный проект не выбран"
            }
        }

        "shows usage when query is empty" {
            runTest {
                val result = handler.execute("/find")

                (result as CommandEffect.Print).isError shouldBe true
                (result as CommandEffect.Print).message shouldContain "Использование"
            }
        }

        "shows usage for /find with only spaces" {
            runTest {
                val result = handler.execute("/find   ")

                (result as CommandEffect.Print).isError shouldBe true
                (result as CommandEffect.Print).message shouldContain "Использование"
            }
        }
    }
})
