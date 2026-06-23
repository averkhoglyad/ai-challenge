package io.averkhogliad.ai.challenge.week2.application.handler

import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.application.planner.FactCollector
import io.averkhogliad.ai.challenge.week2.application.planner.LlmPlanner
import io.averkhogliad.ai.challenge.week2.application.planner.PlanMessageBuilder
import io.averkhogliad.ai.challenge.week2.application.planner.StepParser
import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week2.domain.service.CommandEngine
import io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository
import java.time.Instant

/**
 * Executor для команды `:plan` — планирование шагов выполнения задачи.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация бизнес-операции
 * - **FSM-based** — использует CommandEngine для управления состоянием
 * - **Thin Orchestrator** — делегирует сбор фактов, LLM, парсинг и сообщения специализированным компонентам
 *
 * ## Этапы выполнения
 * 1. **PLANNING** — проверка открытой задачи, запрос description, сбор фактов из LTM
 * 2. **EXECUTION** — делегация планирования в LlmPlanner, парсинг в StepParser
 * 3. **VALIDATION** — показ шагов пользователю, ожидание подтверждения (y/n/edit)
 * 4. **DONE** — сохранение шагов в WM, завершение команды
 */
class PlanCommandHandler(
    private val taskRepository: TaskRepository,
    private val commandEngine: CommandEngine,
    private val factCollector: FactCollector,
    private val llmPlanner: LlmPlanner? = null,
    private val stepParser: StepParser = StepParser(),
    private val invariantService: InvariantService,
    private val messages: PlanMessageBuilder = PlanMessageBuilder(commandEngine)
) {
    val commandName: String = "plan"

    // ==================== PUBLIC API ====================

    suspend fun execute(currentTaskId: Int?): String {
        if (currentTaskId == null)
            return "Ошибка: команда ':plan' требует открытой задачи. Используйте ':open <taskId>' для открытия задачи."
        val taskId = TaskId(currentTaskId.toString())
        val task = taskRepository.findById(taskId)
            ?: return "Ошибка: задача с ID '$currentTaskId' не найдена"
        if (!task.isOpen())
            return "Ошибка: задача '${task.title}' не открыта. Используйте ':open $currentTaskId' для открытия задачи."
        commandEngine.startCommand(commandName, "Проверка задачи и сбор контекста...")
        commandEngine.putContext("taskId", currentTaskId.toString())
        commandEngine.putContext("taskTitle", task.title)
        if (!task.hasDescription()) {
            commandEngine.advanceStep("Description задачи пуст. Пожалуйста, опишите задачу подробно:")
            commandEngine.putContext("needsDescription", "true")
            return "Задача '${task.title}' открыта.\n\nDescription задачи пуст. Пожалуйста, опишите задачу подробно:"
        }
        commandEngine.putContext("description", task.description ?: "")
        storeInvariantsInContext()
        collectAndStoreFacts(task.title, task.description)
        commandEngine.advanceToStage(CommandStage.EXECUTION, "Формирование промпта для LLM...")
        val invariantsCount = commandEngine.getContext("invariantsCount")?.toIntOrNull() ?: 0
        return messages.buildPlanningReadyMessage(task.title, task.description, invariantsCount)
    }

    suspend fun handleDescriptionInput(userInput: String): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"
        if (state.currentStage != CommandStage.PLANNING)
            return "Ошибка: команда ':plan' не ожидает ввода description"
        if (userInput.isBlank())
            return "Ошибка: description не может быть пустым. Пожалуйста, опишите задачу подробно:"
        commandEngine.putContext("description", userInput)
        storeInvariantsInContext()
        val taskTitle = commandEngine.getContext("taskTitle") ?: ""
        collectAndStoreFacts(taskTitle, userInput)
        commandEngine.advanceToStage(CommandStage.EXECUTION, "Формирование промпта для LLM...")
        val invariantsCount = commandEngine.getContext("invariantsCount")?.toIntOrNull() ?: 0
        return messages.buildPlanningReadyMessage(taskTitle, userInput, invariantsCount)
    }

    suspend fun executeExecution(): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"
        if (state.currentStage != CommandStage.EXECUTION)
            return "Ошибка: команда ':plan' не на этапе EXECUTION"
        if (llmPlanner == null)
            return "Ошибка: LLM не настроен. Невозможно выполнить планирование без LLM."
        val taskTitle = commandEngine.getContext("taskTitle") ?: ""
        val description = commandEngine.getContext("description") ?: ""
        val relevantFacts = commandEngine.getContext("relevantFacts") ?: ""
        val invariants = commandEngine.getContext("invariants") ?: ""
        val hasInvariants = invariants.isNotEmpty()
        return when (val planResult =
            llmPlanner.plan(taskTitle, description, relevantFacts, invariants, hasInvariants)) {
            is LlmPlanner.PlanResult.Success -> {
                val steps = stepParser.parse(planResult.response)
                if (steps.isEmpty()) {
                    commandEngine.putContext("executionError", "LLM не вернула список шагов")
                    messages.buildExecutionErrorMessage("LLM не вернула список шагов")
                } else {
                    commandEngine.putContext("generatedSteps", steps.joinToString("\n"))
                    commandEngine.putContext("stepsCount", steps.size.toString())
                    commandEngine.advanceToStage(CommandStage.VALIDATION, "Ожидание подтверждения пользователя...")
                    messages.buildValidationMessage(steps)
                }
            }

            is LlmPlanner.PlanResult.InvariantRefusal -> {
                commandEngine.completeCommand()
                messages.buildInvariantConflictMessage(taskTitle, planResult.conflictingRule)
            }

            is LlmPlanner.PlanResult.Error -> {
                commandEngine.putContext("executionError", planResult.message)
                messages.buildExecutionErrorMessage(planResult.message)
            }
        }
    }

    suspend fun handleValidationInput(userInput: String): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"
        if (state.currentStage != CommandStage.VALIDATION)
            return "Ошибка: команда ':plan' не на этапе VALIDATION"
        return when (userInput.trim().lowercase()) {
            "y", "yes" -> handleValidationConfirm()
            "n", "no" -> handleValidationCancel()
            "edit" -> handleValidationEdit()
            else -> buildString {
                appendLine("❌ Неизвестная команда: '$userInput'")
                appendLine()
                appendLine("Доступные команды:")
                appendLine("  y     — подтвердить план")
                appendLine("  n     — отменить планирование")
                appendLine("  edit  — редактировать шаги")
                appendLine()
                appendLine("Пожалуйста, введите y, n или edit:")
            }
        }
    }

    suspend fun handleUserInput(userInput: String): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"
        return when (state.currentStage) {
            CommandStage.PLANNING -> handleDescriptionInput(userInput)
            CommandStage.EXECUTION -> executeExecution()
            CommandStage.VALIDATION ->
                if (commandEngine.getContext("editMode") == "true") handleEditInput(userInput)
                else handleValidationInput(userInput)

            CommandStage.DONE -> "Ошибка: команда ':plan' уже завершена"
            CommandStage.TERMINATED -> "Ошибка: команда ':plan' была прервана (TERMINATED)"
        }
    }

    suspend fun handleEditInput(userInput: String): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"
        if (state.currentStage != CommandStage.VALIDATION || commandEngine.getContext("editMode") != "true")
            return "Ошибка: режим редактирования не активен"
        val normalized = userInput.trim().lowercase()
        if (normalized == "cancel") {
            commandEngine.putContext("editMode", "false")
            return messages.buildValidationMessage(getContextSteps())
        }
        if (normalized == "done") {
            commandEngine.putContext("editMode", "false")
            return handleValidationConfirm()
        }
        val newSteps = stepParser.parse(userInput)
        if (newSteps.isEmpty())
            return "Ошибка: не удалось распознать шаги. Попробуйте ещё раз или введите 'cancel' для отмены."
        commandEngine.putContext("generatedSteps", newSteps.joinToString("\n"))
        commandEngine.putContext("stepsCount", newSteps.size.toString())
        commandEngine.putContext("editMode", "false")
        return messages.buildValidationMessage(newSteps)
    }

    // ==================== PRIVATE HELPERS ====================

    private suspend fun storeInvariantsInContext() {
        val invariants = invariantService.list()
        commandEngine.putContext(
            "invariants",
            if (invariants.isNotEmpty()) LlmPlanner.buildInvariantsBlock(invariants) else ""
        )
        commandEngine.putContext("invariantsCount", invariants.size.toString())
    }

    private suspend fun collectAndStoreFacts(title: String, description: String?) {
        val facts = factCollector.collect(title, description)
        commandEngine.putContext(
            "relevantFacts",
            if (facts.isNotEmpty()) facts.joinToString("\n") { "• ${it.content}" } else "")
        commandEngine.putContext("factsCount", facts.size.toString())
    }

    private fun getContextSteps(): List<String> =
        (commandEngine.getContext("generatedSteps") ?: "").split("\n").filter { it.isNotBlank() }

    private suspend fun handleValidationConfirm(): String {
        val steps = getContextSteps()
        if (steps.isEmpty()) return "Ошибка: список шагов пуст"
        commandEngine.advanceToStage(CommandStage.DONE, "Сохранение шагов в рабочую память...")
        return executeDone(steps)
    }

    private fun handleValidationCancel(): String {
        commandEngine.completeCommand()
        return "❌ Планирование отменено. Шаги не были сохранены."
    }

    private fun handleValidationEdit(): String = buildString {
        val steps = getContextSteps()
        appendLine("📝 Режим редактирования шагов.")
        appendLine()
        appendLine("Текущие шаги:")
        steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
        appendLine()
        appendLine("Введите новые шаги в том же формате (каждый шаг с новой строки):")
        appendLine("• Пронумерованный список: 1. ..., 2. ..., 3. ...")
        appendLine("• Или маркированный список: - ..., - ..., - ...")
        appendLine()
        appendLine("Для отмены редактирования введите 'cancel'.")
        appendLine("Для подтверждения текущих шагов введите 'done'.")
    }.also { commandEngine.putContext("editMode", "true") }

    private suspend fun executeDone(steps: List<String>): String {
        val taskIdStr = commandEngine.getContext("taskId")
            ?: return "Ошибка: ID задачи не найден в контексте"
        val taskTitle = commandEngine.getContext("taskTitle") ?: "задачи"
        val taskSteps = steps.mapIndexed { index, stepText ->
            TaskStep(
                id = TaskStepId("step-${index + 1}"),
                taskId = TaskId(taskIdStr),
                text = stepText, isCompleted = false, order = index + 1,
                createdAt = Instant.now()
            )
        }
        try {
            taskRepository.saveSteps(TaskId(taskIdStr), taskSteps)
        } catch (e: Exception) {
            return "Ошибка при сохранении шагов: ${e.message}"
        }
        commandEngine.putContext("finalSteps", steps.joinToString("\n"))
        commandEngine.putContext("stepsCount", steps.size.toString())
        commandEngine.completeCommand()
        return buildString {
            appendLine("✅ Этап DONE завершён.")
            appendLine()
            appendLine("📋 План для '$taskTitle' сохранён:")
            steps.forEachIndexed { i, s -> appendLine("  ${i + 1}. $s") }
            appendLine()
            appendLine("💾 Шаги сохранены в базу данных. Всего шагов: ${steps.size}")
            appendLine("🎯 Команда ':plan' завершена успешно.")
        }
    }
}
