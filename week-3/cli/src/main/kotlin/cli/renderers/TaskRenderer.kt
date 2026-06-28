package io.averkhogliad.ai.challenge.week3.cli.cli.renderers

import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.CYAN
import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.GREEN
import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.RED
import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.RESET
import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.YELLOW
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep

/**
 * Специализированный рендерер для задач (todo-manager: список, детали, статусы, шаги).
 *
 * Выделен из [ConsoleCliRenderer] для соблюдения Single Responsibility Principle.
 */
class TaskRenderer {

    fun renderTaskList(tasks: List<Task>) {
        println()
        if (tasks.isEmpty()) {
            println("${YELLOW}📋 Задачи не найдены${RESET}")
        } else {
            println("${CYAN}📋 Список задач:${RESET}")
            tasks.forEach { task ->
                val statusIcon = when (task.status) {
                    TaskStatus.OPEN -> "${YELLOW}○${RESET}"
                    TaskStatus.CLOSED -> "${GREEN}✓${RESET}"
                    TaskStatus.CANCELLED -> "${RED}✗${RESET}"
                }
                println("  $statusIcon [${task.id.value}] ${task.title}")
            }
        }
        println()
    }

    fun renderTaskDetail(task: Task) {
        println()
        println("${CYAN}📋 Задача: ${task.title}${RESET}")
        println("  ${CYAN}ID:${RESET} ${task.id.value}")
        println("  ${CYAN}Статус:${RESET} ${task.status}")
        println("  ${CYAN}Создана:${RESET} ${task.createdAt}")
        println("  ${CYAN}Обновлена:${RESET} ${task.updatedAt}")
        println()
        println("  ${CYAN}Описание:${RESET}")
        if (task.hasDescription()) {
            println("    ${task.description}")
        } else {
            println("    ${YELLOW}(Описание отсутствует. Используйте :edit ${task.id.value} для добавления)${RESET}")
        }
        println()
    }

    fun renderTaskCreated(taskId: TaskId) {
        println()
        println("${GREEN}✅ Задача создана: ${taskId.value}${RESET}")
        println()
    }

    fun renderTaskUpdated(taskId: TaskId) {
        println()
        println("${GREEN}✅ Задача обновлена: ${taskId.value}${RESET}")
        println()
    }

    fun renderTaskDeleted(taskId: TaskId) {
        println()
        println("${GREEN}✅ Задача удалена: ${taskId.value}${RESET}")
        println()
    }

    fun renderTaskClosed(taskId: TaskId) {
        println()
        println("${GREEN}✅ Задача закрыта: ${taskId.value}${RESET}")
        println()
    }

    fun renderTaskCancelled(taskId: TaskId) {
        println()
        println("${YELLOW}⚠️  Задача отменена: ${taskId.value}${RESET}")
        println()
    }

    // ──── Step management ────

    fun renderStepCreated(step: TaskStep) {
        println()
        println("${GREEN}✅ Шаг создан: [${step.id.value}] ${if (step.isCompleted) "[x]" else "[ ]"} ${step.text}${RESET}")
        println()
    }

    fun renderStepList(steps: List<TaskStep>) {
        println()
        if (steps.isEmpty()) {
            println("${YELLOW}👣 Шаги для этой задачи не найдены${RESET}")
        } else {
            println("${CYAN}👣 Список шагов:${RESET}")
            steps.sortedBy { it.order }.forEach { step ->
                val marker = if (step.isCompleted) "${GREEN}[x]${RESET}" else "[ ]"
                println("  $marker [${step.id.value}] ${step.text}")
            }
        }
        println()
    }

    fun renderStepCompleted(step: TaskStep) {
        println()
        println("${GREEN}✅ Шаг выполнен: [${step.id.value}] ${step.text}${RESET}")
        println()
    }

    fun renderStepError(message: String) {
        println()
        println("${RED}❌ [ОШИБКА ШАГА] $message${RESET}")
        println()
    }
}
