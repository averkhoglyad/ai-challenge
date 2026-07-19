package io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto

data class ErrorResponse(
    val error: ErrorDetails,
)

data class ErrorDetails(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)
