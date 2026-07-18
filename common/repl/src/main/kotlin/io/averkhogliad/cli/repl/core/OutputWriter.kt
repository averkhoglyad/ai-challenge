package io.averkhogliad.cli.repl.core

interface OutputWriter {
    suspend fun write(text: String)
    suspend fun writeError(text: String)
    suspend fun writePrompt(prompt: String)
}
