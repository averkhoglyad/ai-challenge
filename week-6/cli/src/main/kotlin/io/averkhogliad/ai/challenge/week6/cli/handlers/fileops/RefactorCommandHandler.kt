package io.averkhogliad.ai.challenge.week6.cli.handlers.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.RefactorUseCase
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class RefactorCommandHandler(
    private val refactorUseCase: RefactorUseCase,
) : CommandHandler {

    override val name: String = "/refactor"
    override val description: String = "Рефакторинг проекта по цели: /refactor <описание изменений>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/refactor" || rawInput.startsWith("/refactor ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val goal = rawInput.removePrefix("/refactor").trim()

        if (goal.isEmpty()) {
            return CommandEffect.Print(
                "Использование: /refactor <описание изменений>\n" +
                        "Пример: /refactor добавь секцию Installation в README.md",
                isError = true,
            )
        }

        return refactorUseCase.execute(goal)
    }
}
