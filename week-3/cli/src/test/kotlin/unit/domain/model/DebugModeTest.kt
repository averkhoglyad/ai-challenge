package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.DebugMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class DebugModeTest : FreeSpec({

    "DebugMode" - {

        "should be disabled by default" {
            val debugMode = DebugMode()
            debugMode.isEnabled shouldBe false
        }

        "should enable debug mode" {
            val debugMode = DebugMode()
            debugMode.enable()
            debugMode.isEnabled shouldBe true
        }

        "should disable debug mode" {
            val debugMode = DebugMode()
            debugMode.enable()
            debugMode.disable()
            debugMode.isEnabled shouldBe false
        }

        "should toggle from disabled to enabled" {
            val debugMode = DebugMode()
            debugMode.toggle()
            debugMode.isEnabled shouldBe true
        }

        "should toggle from enabled to disabled" {
            val debugMode = DebugMode()
            debugMode.enable()
            debugMode.toggle()
            debugMode.isEnabled shouldBe false
        }

        "should set enabled to true" {
            val debugMode = DebugMode()
            debugMode.setEnabled(true)
            debugMode.isEnabled shouldBe true
        }

        "should set enabled to false" {
            val debugMode = DebugMode()
            debugMode.enable()
            debugMode.setEnabled(false)
            debugMode.isEnabled shouldBe false
        }

        "toString should contain isEnabled value" {
            val debugMode = DebugMode()
            debugMode.toString() shouldBe "DebugMode(isEnabled=false)"

            debugMode.enable()
            debugMode.toString() shouldBe "DebugMode(isEnabled=true)"
        }
    }
})
