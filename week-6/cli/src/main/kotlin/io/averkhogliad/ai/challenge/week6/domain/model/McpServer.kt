package io.averkhogliad.ai.challenge.week6.domain.model

import java.time.Instant

data class McpServer(
    val id: String,
    val name: String,
    val serverType: McpServerType = McpServerType.HTTP_SSE,
    val baseUrl: String? = null,
    val transportConfig: String? = null,
    val enabled: Boolean = true,
    val createdAt: Instant,
)
