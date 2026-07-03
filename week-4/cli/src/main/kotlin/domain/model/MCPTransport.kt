package io.averkhogliad.ai.challenge.week4.cli.domain.model

sealed interface MCPTransport {
    data class Stdio(val command: String, val args: List<String> = emptyList()) : MCPTransport
    data class StreamableHttp(val url: String) : MCPTransport
}
