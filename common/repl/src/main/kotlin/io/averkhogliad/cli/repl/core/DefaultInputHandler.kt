package io.averkhogliad.cli.repl.core

interface DefaultInputHandler {
    val description: String
    suspend fun handle(rawInput: String): CommandEffect
}
