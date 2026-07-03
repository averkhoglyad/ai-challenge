package io.averkhogliad.ai.challenge.week4.cli.unit.application.handler

import io.averkhogliad.ai.challenge.week4.cli.application.handler.DebugAction
import io.averkhogliad.ai.challenge.week4.cli.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.domain.model.DebugMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class DebugCommandHandlerTest : FreeSpec({
    lateinit var debugMode: DebugMode
    lateinit var executor: DebugCommandHandler

    beforeEach {
        debugMode = DebugMode()
        executor = DebugCommandHandler(debugMode)
    }

    "TOGGLE" - {
        "execute TOGGLE when disabled enables debug mode" {
            // given
            debugMode.isEnabled shouldBe false

            // when
            val result = executor.execute(DebugAction.TOGGLE)

            // then
            debugMode.isEnabled shouldBe true
            result shouldBe "Debug mode enabled"
        }

        "execute TOGGLE when enabled disables debug mode" {
            // given
            debugMode.enable()
            debugMode.isEnabled shouldBe true

            // when
            val result = executor.execute(DebugAction.TOGGLE)

            // then
            debugMode.isEnabled shouldBe false
            result shouldBe "Debug mode disabled"
        }
    }

    "ON / OFF" - {
        "execute ON enables debug mode" {
            // given
            debugMode.isEnabled shouldBe false

            // when
            val result = executor.execute(DebugAction.ON)

            // then
            debugMode.isEnabled shouldBe true
            result shouldBe "Debug mode enabled"
        }

        "execute ON when already enabled returns message" {
            // given
            debugMode.enable()
            debugMode.isEnabled shouldBe true

            // when
            val result = executor.execute(DebugAction.ON)

            // then
            debugMode.isEnabled shouldBe true
            result shouldBe "Debug mode already enabled"
        }

        "execute OFF disables debug mode" {
            // given
            debugMode.enable()
            debugMode.isEnabled shouldBe true

            // when
            val result = executor.execute(DebugAction.OFF)

            // then
            debugMode.isEnabled shouldBe false
            result shouldBe "Debug mode disabled"
        }

        "execute OFF when already disabled returns message" {
            // given
            debugMode.isEnabled shouldBe false

            // when
            val result = executor.execute(DebugAction.OFF)

            // then
            debugMode.isEnabled shouldBe false
            result shouldBe "Debug mode already disabled"
        }
    }

    "query" - {
        "isEnabled returns current debug mode state" {
            // given
            executor.isEnabled() shouldBe false

            // when
            debugMode.enable()

            // then
            executor.isEnabled() shouldBe true

            // when
            debugMode.disable()

            // then
            executor.isEnabled() shouldBe false
        }

        "commandName is debug" {
            // when & then
            executor.commandName shouldBe "debug"
        }
    }
})
