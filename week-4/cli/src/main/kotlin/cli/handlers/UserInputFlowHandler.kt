package io.averkhogliad.ai.challenge.week4.cli.cli.handlers

import io.averkhogliad.ai.challenge.week4.cli.application.DialogService
import io.averkhogliad.ai.challenge.week4.cli.application.handler.PlanCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagQueryProcessor
import io.averkhogliad.ai.challenge.week4.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagAnswerRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.FallbackReason
import io.averkhogliad.ai.challenge.week4.cli.domain.service.CommandEngine


/**
 * Обработчик пользовательского ввода (prompt-ов).
 *
 * Оркестрирует флоу: парсинг → RAG (если Task 2/3 и включён) / plain LLM → рендеринг.
 * Интегрирует [RagQueryProcessor] для Task 2 и Task 3 с доступом к [CliState.ragState].
 */
class UserInputFlowHandler(
    private val renderer: CliRenderer,
    private val dialogService: DialogService,
    private val planCommandHandler: PlanCommandHandler,
    private val commandEngine: CommandEngine,
    private val commandHandler: CommandHandler,
    private val indexRepository: IndexRepository,
    private val ragQueryProcessor: RagQueryProcessor?,
) {
    suspend fun handle(command: Command.UserInput, state: CliState): CliState {
        if (commandEngine.hasActiveCommand()) {
            val activeState = commandEngine.getActiveState()
            if (activeState?.commandName == "plan") {
                val result = planCommandHandler.handleUserInput(command.text)
                renderer.renderInfo(result)
                return state
            }
        }

        if (state.currentTodoTaskId != null || state.taskListMode) {
            renderer.renderLoadingStart("Общение с ассистентом...")
            val result =
                dialogService.chat(command.text, state.sessionLevel(), state.currentTodoTaskId?.let { TaskId(it) })
            renderer.renderLoadingStop()
            renderTaskResult(result)
            return state
        }

        if (state.currentTaskId != null) {
            // Task 2 и Task 3 с RAG: прямой вызов RagQueryProcessor с доступом к ragState
            if ((state.currentTaskId == 2 || state.currentTaskId == 3) && ragQueryProcessor != null) {
                return handleRagQuery(command, state)
            }

            // Остальные задачи: стандартный флоу через executor
            renderer.renderRequestInfo(command.text, state.executionConfig)
            renderer.renderLoadingStart("Отправка запроса...")
            val (newState, result) = commandHandler.executeUserInput(command, state)
            renderer.renderLoadingStop()
            renderTaskResult(result) { renderer.renderMenu(commandHandler.getAllExecutors()) }
            return newState
        }

        renderer.renderLoadingStart("Общение с ассистентом...")
        val result = dialogService.chat(command.text, state.sessionLevel(), null)
        renderer.renderLoadingStop()
        renderTaskResult(result)
        return state

    }

    /**
     * Обрабатывает запрос через RAG для Task 2.
     */
    private suspend fun handleRagQuery(command: Command.UserInput, state: CliState): CliState {
        val ragState = state.ragState

        // Конфигурационный блок RAG
        renderRagConfigBlock(state)

        // Выполнение RAG-запроса
        renderer.renderLoadingStart(
            if (ragState.enabled) "Поиск по индексам и отправка запроса..."
            else "Отправка запроса..."
        )
        val ragAnswer = ragQueryProcessor!!.process(command.text, ragState, state.executionConfig)
        renderer.renderLoadingStop()

        // Предупреждения при fallback — switch по причине
        if (ragAnswer.fallbackToPlain && ragAnswer.fallbackReason != null) {
            when (ragAnswer.fallbackReason) {
                FallbackReason.NO_ACTIVE_INDEX -> RagAnswerRenderer.renderFallbackWarningNoIndex()
                FallbackReason.EMPTY_SEARCH -> RagAnswerRenderer.renderFallbackWarningEmptySearch(ragState.similarityThreshold)
                FallbackReason.EMBEDDING_ERROR -> RagAnswerRenderer.renderFallbackWarningEmbeddingError()
                FallbackReason.SEARCH_ERROR -> RagAnswerRenderer.renderFallbackWarningSearchError()
                FallbackReason.LLM_ERROR -> { /* handled below via isLlmError */
                }

                FallbackReason.RAG_DISABLED -> { /* обычный LLM, без предупреждения */
                }
            }
        }

        // Ответ LLM — проверяем ошибку
        if (ragAnswer.isLlmError) {
            renderer.renderError(ragAnswer.llmError ?: "Неизвестная ошибка LLM")
        } else {
            renderer.renderResult(TaskResult.Success(ragAnswer.answer))
        }

        // Секция источников
        if (ragAnswer.sources.isNotEmpty()) {
            RagAnswerRenderer.renderSources(ragAnswer.sources)
        }

        // Статистика запроса (Task 3)
        if (ragAnswer.searchContext != null) {
            RagAnswerRenderer.renderStatsSummary(ragAnswer.searchContext.stats)
        }

        return state
    }

    private fun CliState.sessionLevel(): SessionLevel =
        if (currentTodoTaskId != null) SessionLevel.TASK_DETAIL else SessionLevel.TASK_LIST

    private fun renderTaskResult(result: TaskResult?, onNull: () -> Unit = {}) {
        when (result) {
            is TaskResult.Success -> renderer.renderResult(result)
            is TaskResult.Error -> renderer.renderError(result.message)
            is TaskResult.Partial -> renderer.renderResult(result)
            null -> onNull()
        }
    }

    /**
     * Показывает конфигурационный блок RAG перед LLM-запросом.
     */
    private suspend fun renderRagConfigBlock(state: CliState) {
        val ragState = state.ragState
        val activeRunId = indexRepository.getActiveIndex()
        val run = if (activeRunId != null) indexRepository.getRun(activeRunId) else null

        RagAnswerRenderer.renderConfigBlock(
            config = state.executionConfig,
            ragState = ragState,
            activeRunId = activeRunId?.toString(),
            strategy = run?.strategy?.name,
            chunkCount = run?.totalChunks
        )
    }
}
