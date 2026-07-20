package io.averkhogliad.cli.repl.engine

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.dispatcher.ContextStack

class HelpHandler(private val contextStack: ContextStack) : CommandHandler {
    override val name: String = "/help"
    override val aliases: List<String> = listOf("/h")
    override val description: String = "Show available commands"

    override suspend fun execute(rawInput: String): CommandEffect {
        val output = buildString {
            contextStack.chain().forEachIndexed { index, context ->
                if (index > 0) appendLine()
                appendLine("[${context.name}]")
                context.handlers.forEach { handler ->
                    val aliases = handler.aliases.joinToString(" ").let { if (it.isEmpty()) "" else " $it" }
                    appendLine("  ${handler.name}$aliases — ${handler.description}")
                }
                context.defaultHandler?.let { default ->
                    appendLine("  (default) — ${default.description}")
                }
            }
        }
        return CommandEffect.Print(output.trimEnd())
    }
}
