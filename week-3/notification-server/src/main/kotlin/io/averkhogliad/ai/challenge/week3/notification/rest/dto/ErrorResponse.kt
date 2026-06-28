package io.averkhogliad.ai.challenge.week3.notification.rest.dto

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    VALIDATION_ERROR,
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
