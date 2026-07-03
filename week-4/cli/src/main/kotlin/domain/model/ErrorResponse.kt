package io.averkhogliad.ai.challenge.week4.cli.domain.model

import kotlinx.serialization.Serializable

/**
 * DTO для ошибок от внешних сервисов (events-server, notification-server).
 *
 * Формат ответа при ошибке:
 * ```json
 * { "error": { "code": "NOT_FOUND", "message": "...", "details": null } }
 * ```
 */
@Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)
