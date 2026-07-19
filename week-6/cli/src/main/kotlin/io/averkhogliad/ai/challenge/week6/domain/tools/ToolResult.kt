package io.averkhogliad.ai.challenge.week6.domain.tools

sealed interface ToolResult {
    data class Success(val content: String) : ToolResult
    data class Error(val message: String) : ToolResult
    data class PendingConfirm(val message: String) : ToolResult
}
