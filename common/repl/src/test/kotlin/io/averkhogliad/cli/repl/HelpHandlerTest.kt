package io.averkhogliad.cli.repl

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.core.ReplContext
import io.averkhogliad.cli.repl.dispatcher.ContextStack
import io.averkhogliad.cli.repl.engine.HelpHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class HelpHandlerTest {

    @Test
    fun `help output contains command names and descriptions`() = runTest {
        val handler = object : CommandHandler {
            override val name: String = "/foo"
            override val aliases: List<String> = listOf("/f")
            override val description: String = "Does foo"
            override suspend fun execute(rawInput: String): CommandEffect = CommandEffect.None
        }

        val context = object : ReplContext {
            override val name: String = "test"
            override val prompt: String = "test> "
            override val handlers: List<CommandHandler> = listOf(handler)
        }

        val stack = ContextStack(context)
        val helpHandler = HelpHandler(stack)
        val effect = helpHandler.execute("/help")

        assertIs<CommandEffect.Print>(effect)
        assertContains(effect.message, "[test]")
        assertContains(effect.message, "/foo")
        assertContains(effect.message, "Does foo")
    }
}
