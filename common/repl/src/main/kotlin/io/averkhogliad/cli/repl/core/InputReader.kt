package io.averkhogliad.cli.repl.core

interface InputReader {
    suspend fun readLine(): String?
}
