package io.averkhogliad.ai.challenge.week6.cli.contexts

import io.averkhogliad.ai.challenge.week6.application.ListProjectsUseCase
import io.averkhogliad.ai.challenge.week6.application.OpenProjectUseCase
import io.averkhogliad.ai.challenge.week6.cli.handlers.ListProjectsCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.OpenCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.mcp.*
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.core.DefaultInputHandler
import io.averkhogliad.cli.repl.core.ReplContext

class OnboardingContext(
    private val openProjectUseCase: OpenProjectUseCase,
    private val listProjectsUseCase: ListProjectsUseCase,
    private val mcpListHandler: McpListHandler? = null,
    private val mcpAddHandler: McpAddHandler? = null,
    private val mcpRemoveHandler: McpRemoveHandler? = null,
    private val mcpEnableHandler: McpEnableHandler? = null,
    private val mcpInfoHandler: McpInfoHandler? = null,
    private val mcpReconnectHandler: McpReconnectHandler? = null,
) : ReplContext {

    override val name: String = "onboarding"
    override val prompt: String = "onboarding> "

    override val handlers: List<CommandHandler> = listOfNotNull(
        OpenCommandHandler(openProjectUseCase),
        ListProjectsCommandHandler(listProjectsUseCase),
        mcpListHandler,
        mcpAddHandler,
        mcpRemoveHandler,
        mcpEnableHandler,
        mcpInfoHandler,
        mcpReconnectHandler,
    )

    override val defaultHandler: DefaultInputHandler = object : DefaultInputHandler {
        override val description: String = "Подсказка для нового пользователя"
        override suspend fun handle(rawInput: String): CommandEffect =
            CommandEffect.Print("Сначала откройте проект: /open <path>")
    }
}
