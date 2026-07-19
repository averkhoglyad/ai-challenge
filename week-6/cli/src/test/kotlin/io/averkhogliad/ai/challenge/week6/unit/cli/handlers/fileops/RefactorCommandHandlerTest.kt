package io.averkhogliad.ai.challenge.week6.unit.cli.handlers.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.RefactorUseCase
import io.averkhogliad.ai.challenge.week6.cli.handlers.fileops.RefactorCommandHandler
import io.averkhogliad.cli.repl.core.CommandEffect
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class RefactorCommandHandlerTest : FreeSpec({

    lateinit var useCase: RefactorUseCase
    lateinit var handler: RefactorCommandHandler

    beforeEach {
        useCase = mockk()
        handler = RefactorCommandHandler(useCase)
    }

    "canHandle" - {

        "returns true for /refactor" {
            handler.canHandle("/refactor") shouldBe true
        }

        "returns true for /refactor with goal" {
            handler.canHandle("/refactor add Installation section") shouldBe true
        }

        "returns false for other commands" {
            handler.canHandle("/other") shouldBe false
        }
    }

    "execute" - {

        "parses goal and calls use case" {
            runTest {
                val expectedEffect = CommandEffect.None
                coEvery { useCase.execute("add Installation section") } returns expectedEffect

                val result = handler.execute("/refactor add Installation section")

                result shouldBe expectedEffect
                coVerify { useCase.execute("add Installation section") }
            }
        }

        "returns error when goal is empty" {
            runTest {
                val result = handler.execute("/refactor")

                (result as CommandEffect.Print).isError shouldBe true
                (result as CommandEffect.Print).message shouldContain "Использование"
            }
        }

        "returns error when goal is only spaces" {
            runTest {
                val result = handler.execute("/refactor   ")

                (result as CommandEffect.Print).isError shouldBe true
            }
        }

        "propagates use case error result" {
            runTest {
                val errorEffect = CommandEffect.Print("Plan failed", isError = true)
                coEvery { useCase.execute("bad goal") } returns errorEffect

                val result = handler.execute("/refactor bad goal")

                result shouldBe errorEffect
            }
        }
    }
})
