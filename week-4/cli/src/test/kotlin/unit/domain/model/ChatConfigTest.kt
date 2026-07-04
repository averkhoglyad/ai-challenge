package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * Тесты для [ChatConfig].
 */
class ChatConfigTest : FreeSpec({

    "Default values" - {

        "should have reasonable defaults" {
            val config = ChatConfig()

            config.historyWindowSize shouldBe 6
            config.nameMaxLength shouldBe 50
            config.autoNameEnabled shouldBe true
            config.taskStateExtractionEnabled shouldBe true
            config.taskStateMaxTerms shouldBe 50
            config.taskStateMaxConstraints shouldBe 50
        }
    }

    "Validation" - {

        "should accept valid historyWindowSize" {
            ChatConfig(historyWindowSize = 5).historyWindowSize shouldBe 5
            ChatConfig(historyWindowSize = 100).historyWindowSize shouldBe 100
        }

        "should throw on zero historyWindowSize" {
            shouldThrow<IllegalArgumentException> {
                ChatConfig(historyWindowSize = 0)
            }
        }

        "should throw on negative historyWindowSize" {
            shouldThrow<IllegalArgumentException> {
                ChatConfig(historyWindowSize = -1)
            }
        }

        "should accept valid nameMaxLength" {
            ChatConfig(nameMaxLength = 10).nameMaxLength shouldBe 10
            ChatConfig(nameMaxLength = 100).nameMaxLength shouldBe 100
        }

        "should throw on zero nameMaxLength" {
            shouldThrow<IllegalArgumentException> {
                ChatConfig(nameMaxLength = 0)
            }
        }

        "should throw on negative nameMaxLength" {
            shouldThrow<IllegalArgumentException> {
                ChatConfig(nameMaxLength = -5)
            }
        }

        "should throw on zero taskStateMaxTerms" {
            shouldThrow<IllegalArgumentException> {
                ChatConfig(taskStateMaxTerms = 0)
            }
        }

        "should throw on zero taskStateMaxConstraints" {
            shouldThrow<IllegalArgumentException> {
                ChatConfig(taskStateMaxConstraints = 0)
            }
        }
    }

    "Custom configuration" - {

        "should allow disabling autoName" {
            val config = ChatConfig(autoNameEnabled = false)
            config.autoNameEnabled shouldBe false
        }

        "should allow disabling taskStateExtraction" {
            val config = ChatConfig(taskStateExtractionEnabled = false)
            config.taskStateExtractionEnabled shouldBe false
        }
    }
})
