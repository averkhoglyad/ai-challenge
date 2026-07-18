package io.averkhogliad.cli.repl

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.core.DefaultInputHandler
import io.averkhogliad.cli.repl.core.ReplContext
import io.averkhogliad.cli.repl.dispatcher.CommandDispatcher
import io.averkhogliad.cli.repl.dispatcher.ContextStack
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommandDispatcherTest {

    private fun handler(
        name: String,
        aliases: List<String> = emptyList(),
        description: String = "desc",
        effect: CommandEffect
    ) = object : CommandHandler {
        override val name: String = name
        override val aliases: List<String> = aliases
        override val description: String = description
        override suspend fun execute(rawInput: String): CommandEffect = effect
    }

    private fun context(
        name: String,
        handlers: List<CommandHandler> = emptyList(),
        defaultHandler: DefaultInputHandler? = null
    ) = object : ReplContext {
        override val name: String = name
        override val prompt: String = "$name> "
        override val handlers: List<CommandHandler> = handlers
        override val defaultHandler: DefaultInputHandler? = defaultHandler
    }

    @Test
    fun `resolves handler in current context first`() = runTest {
        val rootEffect = CommandEffect.Print("root")
        val childEffect = CommandEffect.Print("child")
        val root = context("root", handlers = listOf(handler("/cmd", effect = rootEffect)))
        val child = context("child", handlers = listOf(handler("/cmd", effect = childEffect)))

        val stack = ContextStack(root)
        stack.push(child)

        val dispatcher = CommandDispatcher(stack)
        val result = dispatcher.dispatch("/cmd")

        assertEquals(childEffect, result)
    }

    @Test
    fun `falls back to parent context`() = runTest {
        val parentEffect = CommandEffect.Print("parent")
        val root = context("root", handlers = listOf(handler("/parent", effect = parentEffect)))
        val child = context("child", handlers = emptyList())

        val stack = ContextStack(root)
        stack.push(child)

        val result = CommandDispatcher(stack).dispatch("/parent")
        assertEquals(parentEffect, result)
    }

    @Test
    fun `matches aliases`() = runTest {
        val effect = CommandEffect.Print("aliased")
        val root = context("root", handlers = listOf(handler("/cmd", aliases = listOf("/c"), effect = effect)))
        val stack = ContextStack(root)

        assertEquals(effect, CommandDispatcher(stack).dispatch("/c"))
    }

    @Test
    fun `default handler handles non slash input`() = runTest {
        val defaultEffect = CommandEffect.Print("default")
        val root = context(
            "root",
            defaultHandler = object : DefaultInputHandler {
                override val description: String = "default"
                override suspend fun handle(rawInput: String): CommandEffect = defaultEffect
            }
        )
        val stack = ContextStack(root)

        assertEquals(defaultEffect, CommandDispatcher(stack).dispatch("hello"))
    }

    @Test
    fun `built in handler is used when no context handler matches`() = runTest {
        val builtinEffect = CommandEffect.Print("builtin")
        val root = context("root", handlers = emptyList())
        val stack = ContextStack(root)
        val dispatcher = CommandDispatcher(
            stack,
            builtinHandlers = listOf(handler("/built", effect = builtinEffect))
        )

        assertEquals(builtinEffect, dispatcher.dispatch("/built"))
    }

    @Test
    fun `default handler preferred over builtin for unknown slash command`() = runTest {
        val defaultEffect = CommandEffect.Print("default")
        val builtinEffect = CommandEffect.Print("builtin")
        val root = context(
            "root",
            defaultHandler = object : DefaultInputHandler {
                override val description: String = "default"
                override suspend fun handle(rawInput: String): CommandEffect = defaultEffect
            }
        )
        val stack = ContextStack(root)
        val dispatcher = CommandDispatcher(
            stack,
            builtinHandlers = listOf(handler("/other", effect = builtinEffect))
        )

        assertEquals(defaultEffect, dispatcher.dispatch("/unknown"))
    }

    @Test
    fun `unknown slash command returns error`() = runTest {
        val root = context("root", handlers = emptyList())
        val stack = ContextStack(root)

        val result = CommandDispatcher(stack).dispatch("/missing")
        assertIs<CommandEffect.Print>(result)
        assertTrue(result.isError)
        assertEquals("Unknown command: /missing", result.message)
    }

    @Test
    fun `unknown non slash input returns error when no default handler`() = runTest {
        val root = context("root", handlers = emptyList())
        val stack = ContextStack(root)

        val result = CommandDispatcher(stack).dispatch("hello")
        assertIs<CommandEffect.Print>(result)
        assertTrue(result.isError)
    }
}
