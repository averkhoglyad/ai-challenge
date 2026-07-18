package io.averkhogliad.cli.repl

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.core.DefaultInputHandler
import io.averkhogliad.cli.repl.core.DomainError
import io.averkhogliad.cli.repl.core.ReplContext
import io.averkhogliad.cli.repl.engine.ReplEngine
import io.averkhogliad.cli.repl.io.InMemoryInputReader
import io.averkhogliad.cli.repl.io.InMemoryOutputWriter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplEngineTest {

    private fun handler(
        name: String,
        aliases: List<String> = emptyList(),
        description: String = "desc",
        execute: suspend (String) -> CommandEffect
    ) = object : CommandHandler {
        override val name: String = name
        override val aliases: List<String> = aliases
        override val description: String = description
        override suspend fun execute(rawInput: String): CommandEffect = execute(rawInput)
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
    fun `print effect is written to output`() = runTest {
        val ctx = context(
            "root",
            handlers = listOf(
                handler("/hi", execute = { CommandEffect.Print("hello") })
            )
        )
        val output = InMemoryOutputWriter()
        val engine = ReplEngine(ctx, inputReader = InMemoryInputReader(listOf("/hi", "/quit")), outputWriter = output)

        engine.start()

        assertEquals(listOf("hello"), output.outputs)
        assertEquals(listOf("root> ", "root> "), output.prompts)
    }

    @Test
    fun `navigate pushes target context`() = runTest {
        val root = context(
            "root",
            handlers = listOf(
                handler("/sub", execute = { CommandEffect.Navigate("sub") })
            )
        )
        val sub = context("sub", handlers = listOf(handler("/back", execute = { CommandEffect.GoBack })))
        val output = InMemoryOutputWriter()
        val engine = ReplEngine(
            root,
            additionalContexts = listOf(sub),
            inputReader = InMemoryInputReader(listOf("/sub", "/back")),
            outputWriter = output
        )

        engine.start()

        assertEquals(listOf("root> ", "sub> ", "root> "), output.prompts)
    }

    @Test
    fun `exit stops engine`() = runTest {
        val ctx = context(
            "root",
            handlers = listOf(handler("/quit", execute = { CommandEffect.Exit }))
        )
        val output = InMemoryOutputWriter()
        val engine =
            ReplEngine(ctx, inputReader = InMemoryInputReader(listOf("/quit", "/ignored")), outputWriter = output)

        engine.start()

        assertEquals(1, output.prompts.size)
    }

    @Test
    fun `confirm y invokes onConfirm`() = runTest {
        val ctx = context(
            "root",
            handlers = listOf(
                handler("/del") {
                    CommandEffect.Confirm(
                        message = "are you sure?",
                        onConfirm = { CommandEffect.Print("deleted") },
                        onCancel = { CommandEffect.Print("cancelled") }
                    )
                }
            )
        )
        val output = InMemoryOutputWriter()
        val engine = ReplEngine(ctx, inputReader = InMemoryInputReader(listOf("/del", "y")), outputWriter = output)

        engine.start()

        assertContains(output.outputs, "are you sure?")
        assertContains(output.outputs, "deleted")
    }

    @Test
    fun `confirm n invokes onCancel`() = runTest {
        val ctx = context(
            "root",
            handlers = listOf(
                handler("/del") {
                    CommandEffect.Confirm(
                        message = "are you sure?",
                        onConfirm = { CommandEffect.Print("deleted") },
                        onCancel = { CommandEffect.Print("cancelled") }
                    )
                }
            )
        )
        val output = InMemoryOutputWriter()
        val engine = ReplEngine(ctx, inputReader = InMemoryInputReader(listOf("/del", "no")), outputWriter = output)

        engine.start()

        assertContains(output.outputs, "are you sure?")
        assertContains(output.outputs, "cancelled")
    }

    @Test
    fun `multiline mode collects input until blank line`() = runTest {
        val ctx = context(
            "root",
            handlers = listOf(
                handler("/multi") {
                    CommandEffect.EnterMultilineMode(
                        prompt = "> ",
                        onComplete = { text -> CommandEffect.Print("got: $text") }
                    )
                }
            )
        )
        val output = InMemoryOutputWriter()
        val engine = ReplEngine(
            ctx,
            inputReader = InMemoryInputReader(listOf("/multi", "line1", "line2", "", "/quit")),
            outputWriter = output
        )

        engine.start()

        assertContains(output.outputs, "got: line1\nline2")
    }

    @Test
    fun `stream output writes each chunk`() = runTest {
        val ctx = context(
            "root",
            handlers = listOf(
                handler("/stream") {
                    CommandEffect.StreamOutput(
                        flow {
                            emit("a")
                            emit("b")
                            emit("c")
                        }
                    )
                }
            )
        )
        val output = InMemoryOutputWriter()
        val engine = ReplEngine(
            ctx,
            inputReader = InMemoryInputReader(listOf("/stream", "/quit")),
            outputWriter = output
        )

        engine.start()

        assertEquals(listOf("a", "b", "c"), output.outputs)
    }

    @Test
    fun `domain error is written as error`() = runTest {
        data class TestError(override val message: String) : DomainError

        val ctx = context(
            "root",
            handlers = listOf(
                handler("/err") { CommandEffect.DisplayDomainError(TestError("domain issue")) }
            )
        )
        val output = InMemoryOutputWriter()
        val engine = ReplEngine(ctx, inputReader = InMemoryInputReader(listOf("/err")), outputWriter = output)

        engine.start()

        assertEquals(listOf("domain issue"), output.errors)
    }

    @Test
    fun `system error from handler is written as error`() = runTest {
        val ctx = context(
            "root",
            handlers = listOf(
                handler("/fail") { throw RuntimeException("boom") }
            )
        )
        val output = InMemoryOutputWriter()
        val engine = ReplEngine(ctx, inputReader = InMemoryInputReader(listOf("/fail")), outputWriter = output)

        engine.start()

        assertEquals(1, output.errors.size)
        assertContains(output.errors.first(), "boom")
    }

    @Test
    fun `eof stops engine`() = runTest {
        val ctx = context("root")
        val output = InMemoryOutputWriter()
        val engine = ReplEngine(ctx, inputReader = InMemoryInputReader(emptyList()), outputWriter = output)

        engine.start()

        assertTrue(output.prompts.isNotEmpty())
        assertEquals(emptyList(), output.outputs)
    }
}
