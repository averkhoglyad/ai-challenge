package io.averkhogliad.cli.repl

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.core.ReplContext
import io.averkhogliad.cli.repl.dispatcher.ContextStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ContextStackTest {

    private fun fakeContext(name: String) = object : ReplContext {
        override val name: String = name
        override val prompt: String = "$name> "
        override val handlers: List<CommandHandler> = emptyList()
    }

    @Test
    fun `current returns initial context`() {
        val initial = fakeContext("root")
        val stack = ContextStack(initial)
        assertSame(initial, stack.current)
    }

    @Test
    fun `push and pop update current`() {
        val root = fakeContext("root")
        val child = fakeContext("child")
        val stack = ContextStack(root)

        stack.push(child)
        assertSame(child, stack.current)
        assertEquals(listOf(child, root), stack.chain())

        val popped = stack.pop()
        assertSame(child, popped)
        assertSame(root, stack.current)
    }

    @Test
    fun `pop on single context clears stack and returns null`() {
        val root = fakeContext("root")
        val stack = ContextStack(root)

        val popped = stack.pop()
        assertNull(popped)
        assertEquals(emptyList(), stack.chain())
    }

    @Test
    fun `chain returns top down order`() {
        val a = fakeContext("a")
        val b = fakeContext("b")
        val c = fakeContext("c")
        val stack = ContextStack(a)

        stack.push(b)
        stack.push(c)

        assertEquals(listOf(c, b, a), stack.chain())
    }

    @Test
    fun `findByName resolves registered contexts`() {
        val root = fakeContext("root")
        val child = fakeContext("child")
        val stack = ContextStack(root)
        stack.push(child)

        assertSame(child, stack.findByName("child"))
        assertSame(root, stack.findByName("root"))
        assertNull(stack.findByName("missing"))
    }
}
