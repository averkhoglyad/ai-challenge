package io.averkhogliad.ai.challenge.week4.cli.cli

interface CliInput {
    fun readLine(): String?

    fun readMultiline(): String
}

class ConsoleCliInput : CliInput {
    override fun readLine(): String? = readlnOrNull()

    override fun readMultiline(): String {
        val lines = mutableListOf<String>()
        while (true) {
            val line = readLine() ?: break
            if (line.isEmpty()) break
            lines.add(line)
        }
        return lines.joinToString("\n")
    }
}
