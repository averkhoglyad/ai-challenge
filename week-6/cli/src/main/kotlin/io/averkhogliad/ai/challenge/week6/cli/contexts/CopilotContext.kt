package io.averkhogliad.ai.challenge.week6.cli.contexts

import io.averkhogliad.ai.challenge.week6.application.AgentLoopService
import io.averkhogliad.ai.challenge.week6.application.ListProjectsUseCase
import io.averkhogliad.ai.challenge.week6.application.OpenProjectUseCase
import io.averkhogliad.ai.challenge.week6.cli.handlers.AskCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.ListProjectsCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.OpenCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.SupportCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.mcp.*
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import io.averkhogliad.cli.repl.core.DefaultInputHandler
import io.averkhogliad.cli.repl.core.ReplContext

class CopilotContext(
    private val openProjectUseCase: OpenProjectUseCase,
    private val listProjectsUseCase: ListProjectsUseCase,
    private val agentLoopService: AgentLoopService? = null,
    private val mcpListHandler: McpListHandler? = null,
    private val mcpAddHandler: McpAddHandler? = null,
    private val mcpRemoveHandler: McpRemoveHandler? = null,
    private val mcpEnableHandler: McpEnableHandler? = null,
    private val mcpInfoHandler: McpInfoHandler? = null,
    private val mcpReconnectHandler: McpReconnectHandler? = null,
    private val supportCommandHandler: SupportCommandHandler? = null,
) : ReplContext {

    override val name: String = "copilot"
    override val prompt: String = "copilot> "

    override val handlers: List<CommandHandler> = listOfNotNull(
        OpenCommandHandler(openProjectUseCase),
        ListProjectsCommandHandler(listProjectsUseCase),
        if (agentLoopService != null) AskCommandHandler(agentLoopService) else null,
        mcpListHandler,
        mcpAddHandler,
        mcpRemoveHandler,
        mcpEnableHandler,
        mcpInfoHandler,
        mcpReconnectHandler,
        supportCommandHandler,
    )

    override val defaultHandler: DefaultInputHandler = object : DefaultInputHandler {
        override val description: String = "Ответ на произвольный запрос через агента"
        override suspend fun handle(rawInput: String): CommandEffect {
            val service = agentLoopService
            if (service == null) {
                return CommandEffect.Print(
                    "Готов помочь с проектом. Попробуйте /ask или задайте вопрос."
                )
            }
            return CommandEffect.StreamOutput(service.processQuery(rawInput))
        }
    }
}
