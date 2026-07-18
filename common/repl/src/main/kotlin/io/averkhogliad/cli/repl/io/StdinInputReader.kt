package io.averkhogliad.cli.repl.io

import io.averkhogliad.cli.repl.core.InputReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StdinInputReader : InputReader {
    override suspend fun readLine(): String? = withContext(Dispatchers.IO) {
        readlnOrNull()
    }
}
