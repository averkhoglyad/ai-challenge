package io.averkhogliad.ai.challenge.week6.unit.cli.handlers.fileops

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.fileops.ProjectSettingsRepository
import io.averkhogliad.ai.challenge.week6.cli.handlers.fileops.ConfigExclusionsHandler
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import io.averkhogliad.cli.repl.core.CommandEffect
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class ConfigExclusionsHandlerTest : FreeSpec({

    lateinit var settingsRepo: ProjectSettingsRepository
    lateinit var projectContextProvider: ProjectContextProvider
    lateinit var handler: ConfigExclusionsHandler
    lateinit var rootPath: Path

    fun makeCtx(): ProjectContext = ProjectContext(
        projectId = "test",
        rootPath = rootPath,
        docsPaths = emptyList(),
        isGitEnabled = false,
    )

    beforeEach {
        settingsRepo = mockk(relaxed = true)
        projectContextProvider = mockk()
        handler = ConfigExclusionsHandler(settingsRepo, projectContextProvider)
        rootPath = createTempDirectory("exclusions-config-test-")
    }

    afterEach {
        rootPath.toFile().deleteRecursively()
    }

    "canHandle matches bare /config exclusions" {
        handler.canHandle("/config exclusions") shouldBe true
    }

    "canHandle matches /config exclusions show" {
        handler.canHandle("/config exclusions show") shouldBe true
    }

    "canHandle matches /config exclusions add pattern" {
        handler.canHandle("/config exclusions add .cache") shouldBe true
    }

    "canHandle matches /config exclusions remove pattern" {
        handler.canHandle("/config exclusions remove node_modules") shouldBe true
    }

    "canHandle matches /config exclusions reset" {
        handler.canHandle("/config exclusions reset") shouldBe true
    }

    "canHandle rejects other commands" {
        handler.canHandle("/config") shouldBe false
        handler.canHandle("/find") shouldBe false
        handler.canHandle("random text") shouldBe false
    }

    "show displays current exclusions" {
        runTest {
            val ctx = makeCtx()
            coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
            every { settingsRepo.loadExclusions(any()) } returns listOf(".custom", "*.out")

            val result = handler.execute("/config exclusions show")

            val print = result as CommandEffect.Print
            print.isError shouldBe false
            print.message shouldContain "Текущие исключения"
            print.message shouldContain ".custom"
            print.message shouldContain "*.out"
            print.message shouldContain "Добавлены пользователем"
            verify(exactly = 1) { settingsRepo.loadExclusions(any()) }
        }
    }

    "add saves new pattern and returns success" {
        runTest {
            val ctx = makeCtx()
            coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
            every { settingsRepo.loadExclusions(any()) } returns emptyList()
            every { settingsRepo.saveExclusions(any(), any()) } returns DomainResult.Success(Unit)

            val result = handler.execute("/config exclusions add .cache")

            val print = result as CommandEffect.Print
            print.isError shouldBe false
            print.message shouldContain "добавлен"
            verify(exactly = 1) { settingsRepo.saveExclusions(any(), any()) }
        }
    }

    "add rejects duplicate pattern" {
        runTest {
            val ctx = makeCtx()
            coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
            every { settingsRepo.loadExclusions(any()) } returns listOf(".cache")

            val result = handler.execute("/config exclusions add .cache")

            val print = result as CommandEffect.Print
            print.isError shouldBe true
            print.message shouldContain "уже в списке"
            verify(exactly = 0) { settingsRepo.saveExclusions(any(), any()) }
        }
    }

    "remove deletes pattern" {
        runTest {
            val ctx = makeCtx()
            coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
            every { settingsRepo.loadExclusions(any()) } returns listOf(".cache", "*.log")
            every { settingsRepo.saveExclusions(any(), any()) } returns DomainResult.Success(Unit)

            val result = handler.execute("/config exclusions remove .cache")

            val print = result as CommandEffect.Print
            print.isError shouldBe false
            print.message shouldContain "удалён"
            verify(exactly = 1) { settingsRepo.saveExclusions(any(), any()) }
        }
    }

    "remove rejects non-existent pattern" {
        runTest {
            val ctx = makeCtx()
            coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
            every { settingsRepo.loadExclusions(any()) } returns listOf(".git", "build")

            val result = handler.execute("/config exclusions remove .cache")

            val print = result as CommandEffect.Print
            print.isError shouldBe true
            print.message shouldContain "не найден"
            verify(exactly = 0) { settingsRepo.saveExclusions(any(), any()) }
        }
    }

    "reset saves empty list" {
        runTest {
            val ctx = makeCtx()
            coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
            every { settingsRepo.saveExclusions(any(), any()) } returns DomainResult.Success(Unit)

            val result = handler.execute("/config exclusions reset")

            val print = result as CommandEffect.Print
            print.isError shouldBe false
            print.message shouldContain "сброшены"
            verify(exactly = 1) { settingsRepo.saveExclusions(any(), any()) }
        }
    }

    "returns error when no active project" {
        runTest {
            coEvery { projectContextProvider.getContext() } returns DomainResult.Success(null)

            val result = handler.execute("/config exclusions show")

            val print = result as CommandEffect.Print
            print.isError shouldBe true
        }
    }

    "returns error for unknown subcommand" {
        runTest {
            val ctx = makeCtx()
            coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
            every { settingsRepo.loadExclusions(any()) } returns emptyList()

            val result = handler.execute("/config exclusions unknown")

            val print = result as CommandEffect.Print
            print.isError shouldBe true
            print.message shouldContain "Unknown subcommand"
        }
    }
})
