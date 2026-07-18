package io.averkhogliad.cli.repl.core

interface CommandHandler {
    val name: String
    val aliases: List<String> get() = emptyList()
    val description: String

    fun canHandle(rawInput: String): Boolean =
        rawInput == name || rawInput in aliases

    suspend fun execute(rawInput: String): CommandEffect
}
