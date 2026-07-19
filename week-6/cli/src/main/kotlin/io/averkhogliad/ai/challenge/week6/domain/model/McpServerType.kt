package io.averkhogliad.ai.challenge.week6.domain.model

enum class McpServerType(val value: String) {
    HTTP_SSE("http_sse"),
    STDIO("stdio"),
    HTTP_STREAMABLE("http_streamable"),
}
