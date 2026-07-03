package io.averkhogliad.ai.challenge.week4.cli.cli

import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.PlanFlowHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.UserInputFlowHandler

class CliCommandDispatcher(
    private val renderer: CliRenderer,
    private val handlers: CliCommandHandlers,
    private val userInputFlowHandler: UserInputFlowHandler,
    private val planFlowHandler: PlanFlowHandler,
) {
    suspend fun handle(command: Command, state: CliState): CliState = when (command) {
        is Command.Help -> state.also { renderer.renderHelp(state) }
        is Command.ShowParameters -> state.also { renderer.renderParameters(state) }
        is Command.Unknown -> state.also { renderer.renderError("Неизвестная команда: ${command.raw}") }

        is Command.SelectTask -> handleSelectTask(command, state)
        is Command.Back -> handlers.todoTask.handleBack(state) {}
        is Command.Quit -> handlers.command.handle(command, state)
        is Command.UserInput -> userInputFlowHandler.handle(command, state)

        is Command.SetTemperature,
        is Command.SetMaxTokens,
        is Command.SetStopSequences,
        is Command.ResetParameters -> handlers.command.handle(command, state)

        is Command.Debug -> state.also { renderer.renderInfo(handlers.debug.execute(command.action)) }
        is Command.ShowState -> handlers.fsm.handleShowState(state)
        is Command.Abort -> handlers.fsm.handleAbort(state)
        is Command.Goto -> state.also { handlers.fsm.handleGoto(state) }
        is Command.GotoState -> state.also { handlers.fsm.handleGotoState(command, state) }

        is Command.InvariantAdd -> state.also { handlers.invariant.handleInvariantAdd(command, state) }
        is Command.InvariantList -> state.also { handlers.invariant.handleInvariantList(state) }
        is Command.InvariantRemove -> state.also { handlers.invariant.handleInvariantRemove(command, state) }

        is Command.NewDialog,
        is Command.ListDialogs,
        is Command.DeleteDialog,
        is Command.SwitchDialog,
        is Command.ShowHistory -> state.also { renderer.renderInfo("Команды диалогов больше не поддерживаются") }

        is Command.Plan -> planFlowHandler.handlePlan(state)
        is Command.PlanSteps -> planFlowHandler.handlePlanSteps(command, state)

        is Command.SetCompressionEnabled,
        is Command.SetCompressionWindow,
        is Command.SetCompressionBlock,
        is Command.ShowCompressionStatus -> state.also {
            renderer.renderInfo("Команды сжатия контекста временно недоступны")
        }

        is Command.ShowStrategyMenu,
        is Command.SwitchStrategy,
        is Command.ShowCurrentStrategy,
        is Command.CreateBranch,
        is Command.SwitchBranch,
        is Command.ListBranches,
        is Command.CreateCheckpoint,
        is Command.ListCheckpoints,
        is Command.ListFacts,
        is Command.ClearFacts,
        is Command.AddFact,
        is Command.RemoveFact -> state.also { renderer.renderInfo("Команды стратегий временно недоступны") }

        is Command.AddTask -> handlers.todoTask.handleAddTask(command, state)
        is Command.ListTasks -> handlers.todoTask.handleListTasks(state)
        is Command.EditTask -> handlers.todoTask.handleEditTask(command, state)
        is Command.DropTask -> handlers.todoTask.handleDropTask(command, state)
        is Command.OpenTask -> handlers.todoTask.handleOpenTask(command, state)
        is Command.CloseTask -> handlers.todoTask.handleCloseTask(command, state)
        is Command.CancelTask -> handlers.todoTask.handleCancelTask(command, state)

        is Command.AddStep -> handlers.taskStep.handleAddStep(command, state)
        is Command.ListSteps -> handlers.taskStep.handleListSteps(state)
        is Command.CompleteStep -> handlers.taskStep.handleCompleteStep(command, state)

        is Command.ClearMemory -> handlers.memory.handleClearMemory(state)
        is Command.ShowStatus -> handlers.memory.handleShowStatus(state)

        is Command.SaveFact -> handlers.ltm.handleSaveFact(command, state)
        is Command.ListLtmFacts -> handlers.ltm.handleListFacts(state)
        is Command.ForgetFact -> handlers.ltm.handleForgetFact(command, state)
        is Command.SearchFacts -> handlers.ltm.handleSearchFacts(command, state)

        is Command.ProfileList -> handlers.profile.handleProfileList(state)
        is Command.ProfileNew -> handlers.profile.handleProfileNew(command, state)
        is Command.ProfileUse -> handlers.profile.handleProfileUse(command, state)
        is Command.ProfileEdit -> handlers.profile.handleProfileEdit(command, state)
        is Command.ProfileDelete -> handlers.profile.handleProfileDelete(command, state)
        is Command.ProfileShow -> handlers.profile.handleProfileShow(command, state)

        is Command.McpAddServerRequest -> handlers.mcp.handleMcpAddServerRequest(state)
        is Command.McpAddServer -> handlers.mcp.handleMcpAddServer(command, state)
        is Command.McpRemoveServerRequest -> handlers.mcp.handleMcpRemoveServerRequest(state)
        is Command.McpRemoveServer -> handlers.mcp.handleMcpRemoveServer(command, state)
        is Command.McpListServers -> handlers.mcp.handleMcpListServers(state)
        is Command.McpConnectServer -> handlers.mcp.handleMcpConnectServer(command, state)
        is Command.McpDisconnectServer -> handlers.mcp.handleMcpDisconnectServer(command, state)
        is Command.McpToolsServer -> handlers.mcp.handleMcpToolsServer(command, state)

        is Command.CreateEvent -> handlers.events.handleCreateEvent(command, state)
        is Command.ListNotes -> handlers.events.handleListNotes(command, state)

        is Command.Index -> handlers.indexer.handleIndex(command, state)
        is Command.IndexRuns -> handlers.indexer.handleIndexRuns(state)
        is Command.IndexSwitch -> handlers.indexer.handleIndexSwitch(command, state)
        is Command.IndexStats -> handlers.indexer.handleIndexStats(command, state)
        is Command.IndexCompare -> handlers.indexer.handleIndexCompare(command, state)
        is Command.IndexDelete -> handlers.indexer.handleIndexDelete(command, state)
        is Command.IndexDeleteBefore -> handlers.indexer.handleIndexDeleteBefore(command, state)
        is Command.IndexDeleteKeepLast -> handlers.indexer.handleIndexDeleteKeepLast(command, state)
        is Command.IndexClear -> handlers.indexer.handleIndexClear(state)
        is Command.IndexClearAll -> handlers.indexer.handleIndexClearAll(state)
    }

    private fun handleSelectTask(command: Command.SelectTask, state: CliState): CliState {
        return handlers.command.handle(command, state)
    }
}
