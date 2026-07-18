package io.averkhogliad.cli.repl.io

import io.averkhogliad.cli.repl.core.OutputWriter

class InMemoryOutputWriter : OutputWriter {
    private val _outputs = mutableListOf<String>()
    private val _errors = mutableListOf<String>()
    private val _prompts = mutableListOf<String>()

    val outputs: List<String> get() = _outputs.toList()
    val errors: List<String> get() = _errors.toList()
    val prompts: List<String> get() = _prompts.toList()

    override suspend fun write(text: String) {
        _outputs.add(text)
    }

    override suspend fun writeError(text: String) {
        _errors.add(text)
    }

    override suspend fun writePrompt(prompt: String) {
        _prompts.add(prompt)
    }
}
