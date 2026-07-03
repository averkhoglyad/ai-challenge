package io.averkhogliad.ai.challenge.week3.cli.unit.application.handler

import io.averkhogliad.ai.challenge.week3.cli.application.handler.DebugAction
import io.averkhogliad.ai.challenge.week3.cli.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week3.cli.domain.model.DebugMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class DebugCommandHandlerTest : FreeSpec({
    lateinit var debugMode: DebugMode
    lateinit var executor: DebugCommandHandler

    beforeEach {
        debugMode = DebugMode()
        executor = DebugCommandHandler(debugMode)
    }

    "execute TOGGLE when disabled enables debug mode" {
        debugMode.isEnabled shouldBe false

        val result = executor.execute(DebugAction.TOGGLE)

        debugMode.isEnabled shouldBe true
        result shouldBe "Debug mode enabled"
    }

    "execute TOGGLE when enabled disables debug mode" {
        debugMode.enable()
        debugMode.isEnabled shouldBe true

        val result = executor.execute(DebugAction.TOGGLE)

        debugMode.isEnabled shouldBe false
        result shouldBe "Debug mode disabled"
    }

    "execute ON enables debug mode" {
        debugMode.isEnabled shouldBe false

        val result = executor.execute(DebugAction.ON)

        debugMode.isEnabled shouldBe true
        result shouldBe "Debug mode enabled"
    }

    "execute ON when already enabled returns message" {
        debugMode.enable()
        debugMode.isEnabled shouldBe true

        val result = executor.execute(DebugAction.ON)

        debugMode.isEnabled shouldBe true
        result shouldBe "Debug mode already enabled"
    }

    "execute OFF disables debug mode" {
        debugMode.enable()
        debugMode.isEnabled shouldBe true

        val result = executor.execute(DebugAction.OFF)

        debugMode.isEnabled shouldBe false
        result shouldBe "Debug mode disabled"
    }

    "execute OFF when already disabled returns message" {
        debugMode.isEnabled shouldBe false

        val result = executor.execute(DebugAction.OFF)

        debugMode.isEnabled shouldBe false
        result shouldBe "Debug mode already disabled"
    }

    "isEnabled returns current debug mode state" {
        executor.isEnabled() shouldBe false

        debugMode.enable()
        executor.isEnabled() shouldBe true

        debugMode.disable()
        executor.isEnabled() shouldBe false
    }

    "commandName is debug" {
        executor.commandName shouldBe "debug"
    }
})
