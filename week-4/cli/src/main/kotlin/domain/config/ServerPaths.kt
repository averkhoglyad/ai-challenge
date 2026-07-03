package io.averkhogliad.ai.challenge.week4.cli.domain.config

/**
 * Пути к API-эндпоинтам внешних сервисов.
 */
object ServerPaths {
    object Rest {
        const val EVENTS_API = "/api/v1/events"
        const val NOTIFICATIONS_API = "/api/v1/notifications"
    }

    object MCP {
        const val SSE = "/sse"
        const val STREAMABLE_HTTP = "/mcp"
    }
}
