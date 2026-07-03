package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact
import io.averkhogliad.ai.challenge.week3.cli.domain.model.FactId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class FactTest : FreeSpec({

    "Fact" - {

        "FactId" - {

            "should create with valid value" {
                val id = FactId("fact-001")
                id.value shouldBe "fact-001"
            }

            "should create with UUID value" {
                val id = FactId("550e8400-e29b-41d4-a716-446655440000")
                id.value shouldBe "550e8400-e29b-41d4-a716-446655440000"
            }

            "should throw when value is blank" {
                shouldThrow<IllegalArgumentException> {
                    FactId("")
                }
            }

            "should throw when value is whitespace" {
                shouldThrow<IllegalArgumentException> {
                    FactId("   ")
                }
            }

            "should be equal for same values" {
                FactId("abc") shouldBe FactId("abc")
            }

            "toString should contain value" {
                val id = FactId("fact-42")
                id.value shouldBe "fact-42"
                id.toString().contains("fact-42") shouldBe true
            }
        }

        "should create with valid fields" {
            val now = Instant.now()
            val fact = Fact(
                id = FactId("fact-001"),
                content = "Сегодня я узнал, что Kotlin поддерживает value classes",
                createdAt = now
            )
            fact.id shouldBe FactId("fact-001")
            fact.content shouldBe "Сегодня я узнал, что Kotlin поддерживает value classes"
            fact.createdAt shouldBe now
        }

        "should throw when content is blank" {
            shouldThrow<IllegalArgumentException> {
                Fact(
                    id = FactId("f-1"),
                    content = "",
                    createdAt = Instant.now()
                )
            }
        }

        "should throw when content is whitespace" {
            shouldThrow<IllegalArgumentException> {
                Fact(
                    id = FactId("f-1"),
                    content = "   ",
                    createdAt = Instant.now()
                )
            }
        }

        "should allow content with leading/trailing spaces" {
            val content = "  важно  "
            val fact = Fact(
                id = FactId("f-1"),
                content = content,
                createdAt = Instant.now()
            )
            fact.content shouldBe content
        }

        "should be equal for same facts" {
            val now = Instant.now()
            val f1 = Fact(FactId("a"), "content", now)
            val f2 = Fact(FactId("a"), "content", now)
            f1 shouldBe f2
        }

        "should not be equal when ids differ" {
            val now = Instant.now()
            val f1 = Fact(FactId("a"), "content", now)
            val f2 = Fact(FactId("b"), "content", now)
            f1 shouldNotBe f2
        }

        "should not be equal when content differs" {
            val now = Instant.now()
            val f1 = Fact(FactId("a"), "hello", now)
            val f2 = Fact(FactId("a"), "world", now)
            f1 shouldNotBe f2
        }

        "copy should preserve fields" {
            val now = Instant.now()
            val original = Fact(FactId("f-1"), "оригинал", now)
            val copy = original.copy(content = "копия")
            copy.id shouldBe FactId("f-1")
            copy.content shouldBe "копия"
            copy.createdAt shouldBe now
        }
    }
})
