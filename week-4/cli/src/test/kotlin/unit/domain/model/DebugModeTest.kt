package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.DebugMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * Unit-тесты для модели DebugMode.
 *
 * Проверяют:
 * - Инициализацию по умолчанию (isEnabled = false)
 * - Включение/выключение режима
 * - Переключение (toggle)
 * - Установку явного значения
 */
class DebugModeTest : FreeSpec({

    lateinit var debugMode: DebugMode

    beforeEach {
        debugMode = DebugMode()
    }

    "default state" - {

        "should be disabled by default" {
            // when & then
            debugMode.isEnabled shouldBe false
        }
    }

    "enable" - {

        "should enable debug mode" {
            // when
            debugMode.enable()

            // then
            debugMode.isEnabled shouldBe true
        }
    }

    "disable" - {

        "should disable debug mode" {
            // given
            debugMode.enable()

            // when
            debugMode.disable()

            // then
            debugMode.isEnabled shouldBe false
        }
    }

    "toggle" - {

        "should toggle from disabled to enabled" {
            // when
            debugMode.toggle()

            // then
            debugMode.isEnabled shouldBe true
        }

        "should toggle from enabled to disabled" {
            // given
            debugMode.enable()

            // when
            debugMode.toggle()

            // then
            debugMode.isEnabled shouldBe false
        }
    }

    "setEnabled" - {

        "should set enabled to true" {
            // when
            debugMode.setEnabled(true)

            // then
            debugMode.isEnabled shouldBe true
        }

        "should set enabled to false" {
            // given
            debugMode.enable()

            // when
            debugMode.setEnabled(false)

            // then
            debugMode.isEnabled shouldBe false
        }
    }

    "toString" - {

        "toString should contain isEnabled value" {
            // when
            val result = debugMode.toString()

            // then
            result.contains("isEnabled=false") shouldBe true

            debugMode.enable()
            val resultEnabled = debugMode.toString()
            resultEnabled.contains("isEnabled=true") shouldBe true
        }
    }
})
