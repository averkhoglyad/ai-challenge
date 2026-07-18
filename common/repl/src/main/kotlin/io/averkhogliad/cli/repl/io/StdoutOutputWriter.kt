package io.averkhogliad.cli.repl.io

import io.averkhogliad.cli.repl.core.OutputWriter

class StdoutOutputWriter : OutputWriter {
    override suspend fun write(text: String) {
        println(text)
    }

    override suspend fun writeError(text: String) {
        System.err.println(text)
    }

    override suspend fun writePrompt(prompt: String) {
        print(prompt)
    }
}
