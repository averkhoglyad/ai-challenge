package io.averkhogliad.ai.challenge.week6.cli.handlers

import io.averkhogliad.ai.challenge.week6.application.ListProjectsUseCase
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class ListProjectsCommandHandler(
    private val listProjectsUseCase: ListProjectsUseCase,
) : CommandHandler {

    override val name: String = "/project list"
    override val aliases: List<String> = listOf("/projects")
    override val description: String = "Показать список проектов"

    override suspend fun execute(rawInput: String): CommandEffect {
        return when (val result = listProjectsUseCase.execute()) {
            is DomainResult.Success -> {
                val projects = result.value
                if (projects.isEmpty()) {
                    CommandEffect.Print("Нет зарегистрированных проектов.")
                } else {
                    val lines = buildList {
                        add("Проекты:")
                        projects.forEachIndexed { index, project ->
                            add("  ${index + 1}. ${project.name}  —  ${project.rootPath}")
                        }
                    }
                    CommandEffect.Print(lines.joinToString("\n"))
                }
            }

            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }
}
