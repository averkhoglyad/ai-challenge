package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Fact
import io.averkhogliad.ai.challenge.week4.cli.domain.model.FactId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

/**
 * Unit-тесты для доменной модели Fact и FactId.
 */
class FactTest : FreeSpec({

    "FactId" - {

        "создание с валидным значением" {
            // when
            val id = FactId("fact-001")

            // then
            id.value shouldBe "fact-001"
        }

        "создание с UUID" {
            // when
            val id = FactId("550e8400-e29b-41d4-a716-446655440000")

            // then
            id.value shouldBe "550e8400-e29b-41d4-a716-446655440000"
        }

        "пустое значение вызывает исключение" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                FactId("")
            }
        }

        "только пробелы вызывают исключение" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                FactId("   ")
            }
        }

        "equals для одинаковых значений" {
            // when & then
            FactId("abc") shouldBe FactId("abc")
        }

        "toString возвращает значение" {
            // given
            val id = FactId("fact-42")

            // then
            id.value shouldBe "fact-42"
            id.toString().contains("fact-42") shouldBe true
        }
    }

    "Fact" - {

        "создание с валидными полями" {
            // given
            val now = Instant.now()

            // when
            val fact = Fact(
                id = FactId("fact-001"),
                content = "Сегодня я узнал, что Kotlin поддерживает value classes",
                createdAt = now
            )

            // then
            fact.id shouldBe FactId("fact-001")
            fact.content shouldBe "Сегодня я узнал, что Kotlin поддерживает value classes"
            fact.createdAt shouldBe now
        }

        "пустой content вызывает исключение" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                Fact(
                    id = FactId("f-1"),
                    content = "",
                    createdAt = Instant.now()
                )
            }
        }

        "content из пробелов вызывает исключение" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                Fact(
                    id = FactId("f-1"),
                    content = "   ",
                    createdAt = Instant.now()
                )
            }
        }

        "content с пробелами по краям допустим (trim не применяется)" {
            // given
            val content = "  важно  "

            // when
            val fact = Fact(
                id = FactId("f-1"),
                content = content,
                createdAt = Instant.now()
            )

            // then
            fact.content shouldBe content
        }

        "equals для одинаковых фактов" {
            // given
            val now = Instant.now()
            val f1 = Fact(FactId("a"), "content", now)
            val f2 = Fact(FactId("a"), "content", now)

            // then
            f1 shouldBe f2
        }

        "разные id — разные факты" {
            // given
            val now = Instant.now()
            val f1 = Fact(FactId("a"), "content", now)
            val f2 = Fact(FactId("b"), "content", now)

            // then
            f1 shouldNotBe f2
        }

        "разный content — разные факты" {
            // given
            val now = Instant.now()
            val f1 = Fact(FactId("a"), "hello", now)
            val f2 = Fact(FactId("a"), "world", now)

            // then
            f1 shouldNotBe f2
        }

        "copy сохраняет поля" {
            // given
            val now = Instant.now()
            val original = Fact(FactId("f-1"), "оригинал", now)

            // when
            val copy = original.copy(content = "копия")

            // then
            copy.id shouldBe FactId("f-1")
            copy.content shouldBe "копия"
            copy.createdAt shouldBe now
        }
    }
})
