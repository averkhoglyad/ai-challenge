package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Тесты для value object [SessionId].
 */
class SessionIdTest : FreeSpec({

    "creation" - {

        "should create with valid value" {
            // when
            val id = SessionId("test-session-id")

            // then
            id.value shouldBe "test-session-id"
        }
    }

    "validation" - {

        "should throw when value is blank" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                SessionId("")
            }
        }

        "should throw when value is whitespace" {
            // when & then
            shouldThrow<IllegalArgumentException> {
                SessionId("   ")
            }
        }
    }

    "generation" - {

        "should generate unique id" {
            // when
            val id1 = SessionId.generate()
            val id2 = SessionId.generate()

            // then
            id1 shouldNotBe id2
            id1.value.isNotBlank() shouldBe true
            id2.value.isNotBlank() shouldBe true
        }
    }

    "equality" - {

        "should be equal when values are equal" {
            // given
            val id1 = SessionId("same-value")
            val id2 = SessionId("same-value")

            // then
            id1 shouldBe id2
            id1.hashCode() shouldBe id2.hashCode()
        }

        "should not be equal when values are different" {
            // given
            val id1 = SessionId("value-1")
            val id2 = SessionId("value-2")

            // then
            id1 shouldNotBe id2
        }
    }

    "toString" - {

        "toString should return value" {
            // when
            val id = SessionId("test-value")

            // then
            id.toString() shouldBe "test-value"
        }
    }
})
