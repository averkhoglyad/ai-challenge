package io.averkhogliad.cli.repl.core

interface ReplContext {
    val name: String
    val prompt: String
    val handlers: List<CommandHandler>
    val defaultHandler: DefaultInputHandler? get() = null
}
