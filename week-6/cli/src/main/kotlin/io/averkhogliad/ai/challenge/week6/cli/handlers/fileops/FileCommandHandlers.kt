package io.averkhogliad.ai.challenge.week6.cli.handlers.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.FileInfoUseCase
import io.averkhogliad.ai.challenge.week6.application.fileops.FileReadUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class FileReadCommandHandler(
    private val fileReadUseCase: FileReadUseCase,
) : CommandHandler {

    override val name: String = "/file read"
    override val description: String = "Чтение содержимого файла: /file read <path>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput.startsWith("/file read ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val path = rawInput.removePrefix("/file read").trim()

        if (path.isEmpty()) {
            return CommandEffect.Print("Использование: /file read <path>", isError = true)
        }

        return when (val result = fileReadUseCase.execute(path)) {
            is DomainResult.Success -> {
                val fc = result.value
                val lines = fc.content.lines()
                val numbered = lines.mapIndexed { i, line ->
                    "${"%(4d".format(i + 1)} | $line"
                }.joinToString("\n")

                val header = if (fc.truncated) {
                    "⚠ Файл обрезан до 64KB (реальный размер: ${fc.sizeBytes} байт)\n\n"
                } else ""

                CommandEffect.Print(header + numbered)
            }

            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }
}

class FileInfoCommandHandler(
    private val fileInfoUseCase: FileInfoUseCase,
) : CommandHandler {

    override val name: String = "/file info"
    override val description: String = "Информация о файле: /file info <path>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/file info" || rawInput.startsWith("/file info ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val path = rawInput.removePrefix("/file info").trim()

        if (path.isEmpty()) {
            return CommandEffect.Print("Использование: /file info <path>", isError = true)
        }

        return when (val result = fileInfoUseCase.execute(path)) {
            is DomainResult.Success -> {
                val meta = result.value
                val info = buildString {
                    appendLine("Файл: ${meta.path}")
                    appendLine("Размер: ${formatSize(meta.sizeBytes)}")
                    appendLine("Тип: ${if (meta.isDirectory) "директория" else "файл"}")
                    appendLine("Изменён: ${meta.lastModified}")
                }
                CommandEffect.Print(info)
            }

            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}

class FileListCommandHandler(
    private val fileListUseCase: io.averkhogliad.ai.challenge.week6.application.fileops.FileListUseCase,
) : CommandHandler {

    override val name: String = "/file list"
    override val aliases: List<String> = listOf("/ls")
    override val description: String = "Список файлов в директории: /file list [dir]"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/file list" || rawInput.startsWith("/file list ") ||
                rawInput == "/ls" || rawInput.startsWith("/ls ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val dir = rawInput
            .removePrefix("/file list")
            .removePrefix("/ls")
            .trim()
            .ifEmpty { "." }

        return when (val result = fileListUseCase.execute(dir)) {
            is DomainResult.Success -> {
                val files = result.value
                if (files.isEmpty()) {
                    CommandEffect.Print("Директория пуста: $dir")
                } else {
                    val output = buildString {
                        appendLine("Содержимое $dir (${files.size} элементов):")
                        files.forEach { f ->
                            val type = if (f.isDirectory) "[DIR] " else "[FILE]"
                            val size = if (!f.isDirectory) " ${formatSize(f.sizeBytes)}" else ""
                            val bin = if (f.isBinary) " [BIN]" else ""
                            appendLine("  $type${f.path}$size$bin")
                        }
                    }
                    CommandEffect.Print(output)
                }
            }

            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
