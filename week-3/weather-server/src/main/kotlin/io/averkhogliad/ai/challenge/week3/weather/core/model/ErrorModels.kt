package io.averkhogliad.ai.challenge.week3.weather.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    NOT_FOUND,
    VALIDATION_ERROR,
    INVALID_DATE_FORMAT,
    PROVIDER_UNAVAILABLE,
    INTERNAL_ERROR
}

@Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val code: ErrorCode,
    val message: String,
    val details: Map<String, String>? = null
)
