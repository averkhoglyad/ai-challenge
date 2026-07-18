package io.averkhogliad.cli.repl.dispatcher

import io.averkhogliad.cli.repl.core.ReplContext

class ContextStack(initialContext: ReplContext) {
    private val stack = mutableListOf(initialContext)

    val current: ReplContext
        get() = stack.lastOrNull()
            ?: throw IllegalStateException("Context stack is empty")

    fun push(context: ReplContext) {
        stack.add(context)
    }

    fun pop(): ReplContext? {
        if (stack.size <= 1) {
            stack.clear()
            return null
        }
        return stack.removeAt(stack.lastIndex)
    }

    fun chain(): List<ReplContext> = stack.reversed()

    fun findByName(name: String): ReplContext? = stack.find { it.name == name }
}
