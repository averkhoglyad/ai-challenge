package io.averkhogliad.ai.challenge.week3.cli.cli

import io.averkhogliad.ai.challenge.week3.cli.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week3.cli.cli.handlers.*

data class CliCommandHandlers(
    val command: CommandHandler,
    val debug: DebugCommandHandler,
    val todoTask: TodoTaskCommandHandler,

    val taskStep: TaskStepCommandHandler,
    val memory: MemoryCommandHandler,
    val ltm: LtmCommandHandler,
    val fsm: FsmCommandHandler,
    val invariant: InvariantCommandHandler,
    val profile: ProfileCommandHandler,
    val mcp: MCPCommandHandler,
    val events: EventsCommandHandler,
)
