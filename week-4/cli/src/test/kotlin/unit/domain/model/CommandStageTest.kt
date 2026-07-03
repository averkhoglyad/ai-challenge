package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.CommandStage
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * Тесты для enum [CommandStage].
 *
 * Проверяет:
 * - Наличие всех ожидаемых значений (PLANNING, EXECUTION, VALIDATION, DONE, TERMINATED)
 * - Порядок значений (ordinal)
 * - Возможность получения значений по имени
 */
class CommandStageTest : FreeSpec({

    "enum entries" - {

        "should have exactly 5 stages" {
            // when & then
            CommandStage.entries.size shouldBe 5
        }

        "should have PLANNING as first stage" {
            // when & then
            CommandStage.PLANNING.ordinal shouldBe 0
        }

        "should have EXECUTION as second stage" {
            // when & then
            CommandStage.EXECUTION.ordinal shouldBe 1
        }

        "should have VALIDATION as third stage" {
            // when & then
            CommandStage.VALIDATION.ordinal shouldBe 2
        }

        "should have DONE as fourth stage" {
            // when & then
            CommandStage.DONE.ordinal shouldBe 3
        }

        "should have TERMINATED as fifth stage" {
            // when & then
            CommandStage.TERMINATED.ordinal shouldBe 4
        }

        "entries should be in correct order" {
            // when
            val entries = CommandStage.entries

            // then
            entries[0] shouldBe CommandStage.PLANNING
            entries[1] shouldBe CommandStage.EXECUTION
            entries[2] shouldBe CommandStage.VALIDATION
            entries[3] shouldBe CommandStage.DONE
            entries[4] shouldBe CommandStage.TERMINATED
        }

        "all stages should have non-blank name" {
            // when & then
            CommandStage.entries.forEach { stage ->
                stage.name.isNotBlank() shouldBe true
            }
        }
    }

    "valueOf" - {

        "should be able to get stage by name" {
            // when & then
            CommandStage.valueOf("PLANNING") shouldBe CommandStage.PLANNING
            CommandStage.valueOf("EXECUTION") shouldBe CommandStage.EXECUTION
            CommandStage.valueOf("VALIDATION") shouldBe CommandStage.VALIDATION
            CommandStage.valueOf("DONE") shouldBe CommandStage.DONE
            CommandStage.valueOf("TERMINATED") shouldBe CommandStage.TERMINATED
        }
    }
})
