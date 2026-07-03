package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CommandStageTest : FreeSpec({

    "CommandStage" - {

        "should have exactly 5 stages" {
            CommandStage.entries.size shouldBe 5
        }

        "should have PLANNING as first stage" {
            CommandStage.PLANNING.ordinal shouldBe 0
        }

        "should have EXECUTION as second stage" {
            CommandStage.EXECUTION.ordinal shouldBe 1
        }

        "should have VALIDATION as third stage" {
            CommandStage.VALIDATION.ordinal shouldBe 2
        }

        "should have DONE as fourth stage" {
            CommandStage.DONE.ordinal shouldBe 3
        }

        "should have TERMINATED as fifth stage" {
            CommandStage.TERMINATED.ordinal shouldBe 4
        }

        "should be able to get stage by name" {
            CommandStage.valueOf("PLANNING") shouldBe CommandStage.PLANNING
            CommandStage.valueOf("EXECUTION") shouldBe CommandStage.EXECUTION
            CommandStage.valueOf("VALIDATION") shouldBe CommandStage.VALIDATION
            CommandStage.valueOf("DONE") shouldBe CommandStage.DONE
            CommandStage.valueOf("TERMINATED") shouldBe CommandStage.TERMINATED
        }

        "entries should be in correct order" {
            val entries = CommandStage.entries
            entries[0] shouldBe CommandStage.PLANNING
            entries[1] shouldBe CommandStage.EXECUTION
            entries[2] shouldBe CommandStage.VALIDATION
            entries[3] shouldBe CommandStage.DONE
            entries[4] shouldBe CommandStage.TERMINATED
        }

        "all stages should have non-blank name" {
            CommandStage.entries.forEach { stage ->
                stage.name.isNotBlank() shouldBe true
            }
        }
    }
})
