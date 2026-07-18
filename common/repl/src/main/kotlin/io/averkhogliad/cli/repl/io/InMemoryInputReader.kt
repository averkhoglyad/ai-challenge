package io.averkhogliad.cli.repl.io

import io.averkhogliad.cli.repl.core.InputReader

class InMemoryInputReader(lines: List<String>) : InputReader {
    private val iterator = lines.iterator()

    override suspend fun readLine(): String? =
        if (iterator.hasNext()) iterator.next() else null
}
