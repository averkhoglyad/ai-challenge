package io.averkhogliad.ai.challenge.week3.events.unit.rest

import io.averkhogliad.ai.challenge.week3.events.rest.dto.ErrorCode
import io.averkhogliad.ai.challenge.week3.events.rest.dto.ErrorResponse
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json

class ErrorResponseTest : FreeSpec({

    val json = Json { encodeDefaults = true }

    "ErrorResponse.notFound" - {

        "produces correct NOT_FOUND structure" {
            // given
            val id = "evt-123"

            // when
            val response = ErrorResponse.notFound(id)

            // then
            response.error.code shouldBe ErrorCode.NOT_FOUND
            response.error.message shouldBe "Event not found: evt-123"
            response.error.details shouldBe null
        }
    }

    "ErrorResponse.invalidDateFormat" - {

        "produces correct INVALID_DATE_FORMAT structure" {
            // given
            val field = "date"
            val value = "not-a-date"

            // when
            val response = ErrorResponse.invalidDateFormat(field, value)

            // then
            response.error.code shouldBe ErrorCode.INVALID_DATE_FORMAT
            response.error.message shouldBe "Invalid date format for field 'date': 'not-a-date'"
            response.error.details shouldBe null
        }
    }

    "ErrorResponse.validationError" - {

        "produces correct VALIDATION_ERROR structure" {
            // given
            val message = "Validation failed"
            val details = mapOf("title" to "must not be blank", "date" to "Invalid ISO 8601 date format")

            // when
            val response = ErrorResponse.validationError(message, details)

            // then
            response.error.code shouldBe ErrorCode.VALIDATION_ERROR
            response.error.message shouldBe message
            response.error.details shouldBe details
        }
    }

    "ErrorResponse.internalError" - {

        "produces correct INTERNAL_ERROR structure" {
            // given
            val message = "Something went wrong"

            // when
            val response = ErrorResponse.internalError(message)

            // then
            response.error.code shouldBe ErrorCode.INTERNAL_ERROR
            response.error.message shouldBe message
            response.error.details shouldBe null
        }
    }

    "JSON serialization" - {

        "serializes notFound to JSON correctly" {
            // given
            val response = ErrorResponse.notFound("evt-1")

            // when
            val jsonString = json.encodeToString(response)

            // then
            jsonString shouldContain "\"NOT_FOUND\""
            jsonString shouldContain "Event not found: evt-1"
        }

        "serializes validationError to JSON correctly" {
            // given
            val response = ErrorResponse.validationError(
                "Validation failed",
                mapOf("title" to "must not be blank")
            )

            // when
            val jsonString = json.encodeToString(response)

            // then
            jsonString shouldContain "\"VALIDATION_ERROR\""
            jsonString shouldContain "Validation failed"
            jsonString shouldContain "\"title\""
            jsonString shouldContain "must not be blank"
        }

        "serializes internalError to JSON correctly" {
            // given
            val response = ErrorResponse.internalError("oops")

            // when
            val jsonString = json.encodeToString(response)

            // then
            jsonString shouldContain "\"INTERNAL_ERROR\""
            jsonString shouldContain "oops"
        }

        "serializes invalidDateFormat to JSON correctly" {
            // given
            val response = ErrorResponse.invalidDateFormat("from_date", "xyz")

            // when
            val jsonString = json.encodeToString(response)

            // then
            jsonString shouldContain "\"INVALID_DATE_FORMAT\""
            jsonString shouldContain "from_date"
            jsonString shouldContain "xyz"
        }
    }
})
