package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

class SessionIdTest : FreeSpec({

    "SessionId" - {

        "should create with valid value" {
            val id = SessionId("test-session-id")
            id.value shouldBe "test-session-id"
        }

        "should throw when value is blank" {
            shouldThrow<IllegalArgumentException> {
                SessionId("")
            }
        }

        "should throw when value is whitespace" {
            shouldThrow<IllegalArgumentException> {
                SessionId("   ")
            }
        }

        "should generate unique id" {
            val id1 = SessionId.generate()
            val id2 = SessionId.generate()

            id1 shouldNotBe id2
            id1.value.shouldNotBeBlank()
            id2.value.shouldNotBeBlank()
        }

        "should be equal when values are equal" {
            val id1 = SessionId("same-value")
            val id2 = SessionId("same-value")

            id1 shouldBe id2
            id1.hashCode() shouldBe id2.hashCode()
        }

        "should not be equal when values are different" {
            val id1 = SessionId("value-1")
            val id2 = SessionId("value-2")

            id1 shouldNotBe id2
        }

        "toString should return value" {
            val id = SessionId("test-value")
            id.toString() shouldBe "test-value"
        }
    }
})
