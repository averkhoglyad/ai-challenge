package io.averkhogliad.ai.challenge.week3.events.rest.dto

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    VALIDATION_ERROR,
    INVALID_DATE_FORMAT,
    NOT_FOUND,
    INTERNAL_ERROR,
}

@Serializable
data class ErrorDetail(
    val code: ErrorCode,
    val message: String,
    val details: Map<String, String>? = null,
)

@Serializable
data class ErrorResponse(
    val error: ErrorDetail,
) {
    companion object {
        fun notFound(id: String): ErrorResponse = ErrorResponse(
            error = ErrorDetail(
                code = ErrorCode.NOT_FOUND,
                message = "Event not found: $id",
            )
        )

        fun invalidDateFormat(field: String, receivedValue: String): ErrorResponse = ErrorResponse(
            error = ErrorDetail(
                code = ErrorCode.INVALID_DATE_FORMAT,
                message = "Invalid date format for field '$field': '$receivedValue'",
            )
        )

        fun validationError(message: String, details: Map<String, String>): ErrorResponse = ErrorResponse(
            error = ErrorDetail(
                code = ErrorCode.VALIDATION_ERROR,
                message = message,
                details = details,
            )
        )

        fun internalError(message: String): ErrorResponse = ErrorResponse(
            error = ErrorDetail(
                code = ErrorCode.INTERNAL_ERROR,
                message = message,
            )
        )
    }
}
