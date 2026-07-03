package io.averkhogliad.ai.challenge.week4.cli.cli

import io.averkhogliad.ai.challenge.week4.cli.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.*
import io.averkhogliad.ai.challenge.week4.cli.cli.indexer.IndexCommandHandler

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
    val indexer: IndexCommandHandler,
)
