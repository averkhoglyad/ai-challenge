package io.averkhogliad.ai.challenge.week1.domain.model

data class DomainToolCall(
    val id: String,
    val type: String = "function",
    val function: DomainFunctionCall
)

data class DomainFunctionCall(
    val name: String,
    val arguments: String
)
