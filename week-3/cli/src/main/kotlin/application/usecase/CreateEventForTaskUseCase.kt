package io.averkhogliad.ai.challenge.week3.cli.application.usecase

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.CommandEngine
import io.averkhogliad.ai.challenge.week3.cli.domain.service.EventsClient
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import java.time.Clock
import java.time.LocalDate

class CreateEventForTaskUseCase(
    private val taskRepository: TaskRepository,
    private val eventsClient: EventsClient,
    private val commandEngine: CommandEngine,
    private val memoryService: MemoryService,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    /**
     * Создаёт событие в календаре для задачи.
     *
     * @param taskId ID открытой задачи (должен быть предварительно проверен)
     * @param date дата события (YYYY-MM-DD, не в прошлом)
     * @return Result с обновлённой задачей или ошибкой
     */
    suspend fun execute(taskId: TaskId, date: LocalDate): Result<Task> {
        // 1. Получить задачу
        val task = taskRepository.findById(taskId)
            ?: return Result.failure(NoOpenTaskException())

        // 2. Проверить FSM
        if (commandEngine.hasActiveCommand()) {
            val state = commandEngine.getActiveState()
            if (state != null && state.currentStage in setOf(
                    CommandStage.PLANNING,
                    CommandStage.EXECUTION,
                    CommandStage.VALIDATION
                )
            ) {
                return Result.failure(
                    FSMActiveException("${state.commandName} (${state.currentStage})")
                )
            }
        }

        // 3. Валидация даты
        if (date < LocalDate.now(clock)) {
            return Result.failure(InvalidDateException("Дата не может быть в прошлом"))
        }

        // 4. Если у задачи уже есть событие — удалить его
        if (task.eventId != null) {
            val deleteResult = eventsClient.deleteEvent(task.eventId)
            deleteResult.exceptionOrNull()?.let { e ->
                System.err.println("[WARN] Не удалось удалить предыдущее событие ${task.eventId}: ${e.message}")
            }
        }

        // 5. Создать событие
        val eventResult = eventsClient.createEvent(task.title, date)
        val event = eventResult.getOrElse { e ->
            return Result.failure(e)
        }

        // 6. Обновить задачу
        val updateResult = taskRepository.updateEvent(task.id, event.id, event.date)
        if (updateResult.isFailure) {
            return Result.failure(updateResult.exceptionOrNull()!!)
        }

        // 6a. Обновить WorkingMemory
        memoryService.switchToTaskLevel(taskId)

        // 7. Вернуть обновлённую задачу
        return Result.success(taskRepository.findById(taskId)!!)
    }
}
