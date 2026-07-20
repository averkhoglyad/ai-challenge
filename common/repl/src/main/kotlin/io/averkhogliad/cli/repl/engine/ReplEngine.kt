package io.averkhogliad.cli.repl.engine

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.InputReader
import io.averkhogliad.cli.repl.core.OutputWriter
import io.averkhogliad.cli.repl.core.ReplContext
import io.averkhogliad.cli.repl.dispatcher.CommandDispatcher
import io.averkhogliad.cli.repl.dispatcher.ContextStack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope

import kotlinx.coroutines.isActive

class ReplEngine(
    initialContext: ReplContext,
    additionalContexts: List<ReplContext> = emptyList(),
    private val inputReader: InputReader,
    private val outputWriter: OutputWriter
) {
    private val contextStack = ContextStack(initialContext)
    private val builtinHandlers = listOf(HelpHandler(contextStack), QuitHandler(), BackHandler(contextStack))
    private val dispatcher = CommandDispatcher(contextStack, builtinHandlers)
    private val registeredContexts = mutableMapOf<String, ReplContext>()

    init {
        registerContext(initialContext)
        additionalContexts.forEach(::registerContext)
    }

    private fun registerContext(context: ReplContext) {
        registeredContexts[context.name] = context
    }

    suspend fun start() = coroutineScope {
        var running = true
        while (running && isActive) {
            try {
                outputWriter.writePrompt(contextStack.current.prompt)
                val input = inputReader.readLine()
                if (input == null) {
                    running = false
                    continue
                }

                val effect = try {
                    dispatcher.dispatch(input.trim())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CommandEffect.DisplaySystemError("Command execution failed: ${e.message}", e)
                }

                running = handleEffect(effect)
            } catch (e: CancellationException) {
                running = false
            }
        }
    }

    private suspend fun handleEffect(effect: CommandEffect): Boolean = when (effect) {
        is CommandEffect.Print -> {
            if (effect.isError) outputWriter.writeError(effect.message)
            else outputWriter.write(effect.message)
            true
        }

        is CommandEffect.Navigate -> {
            val target = registeredContexts[effect.targetContextName]
            if (target != null) {
                effect.message?.let { outputWriter.write(it) }
                activateContext(target)
            } else {
                handleEffect(
                    CommandEffect.DisplaySystemError(
                        "Unknown context: ${effect.targetContextName}"
                    )
                )
            }
            true
        }

        is CommandEffect.GoBack -> {
            contextStack.pop()
            if (contextStack.chain().isEmpty()) {
                false
            } else {
                true
            }
        }

        is CommandEffect.Exit -> false
        is CommandEffect.None -> true

        is CommandEffect.StreamOutput -> {
            effect.contentFlow.collect { chunk -> outputWriter.write(chunk) }
            true
        }

        is CommandEffect.Confirm -> {
            outputWriter.write(effect.message)
            val answer = inputReader.readLine()?.trim()?.lowercase()
            val nextEffect = when (answer) {
                "y", "yes" -> effect.onConfirm()
                "n", "no" -> effect.onCancel()
                else -> CommandEffect.None
            }
            handleEffect(nextEffect)
        }

        is CommandEffect.EnterMultilineMode -> {
            val lines = mutableListOf<String>()
            while (true) {
                outputWriter.writePrompt(effect.prompt)
                val line = inputReader.readLine()
                if (line == null || line.isEmpty()) break
                lines.add(line)
            }
            val content = lines.joinToString("\n")
            handleEffect(effect.onComplete(content))
        }

        is CommandEffect.DisplayDomainError<*> -> {
            outputWriter.writeError(effect.error.message)
            true
        }

        is CommandEffect.DisplaySystemError -> {
            outputWriter.writeError(effect.message)
            effect.cause?.printStackTrace()
            true
        }
    }

    private fun activateContext(target: ReplContext) {
        val index = contextStack.chain().indexOfFirst { it.name == target.name }
        when {
            index == 0 -> return
            index > 0 -> repeat(index) { contextStack.pop() }
            else -> contextStack.push(target)
        }
    }
}
