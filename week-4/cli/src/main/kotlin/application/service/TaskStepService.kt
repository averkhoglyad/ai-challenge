package io.averkhogliad.ai.challenge.week4.cli.application.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskStepRepository
import java.time.Clock
import java.util.*

/**
 * Application service for task step use cases and memory synchronization.
 */
class TaskStepService(
    private val taskStepRepository: TaskStepRepository,
    private val memoryService: MemoryService,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun addStep(taskId: TaskId, text: String): TaskStep {
        val step = TaskStep(
            id = TaskStepId(UUID.randomUUID().toString()),
            taskId = taskId,
            text = text,
            isCompleted = false,
            order = taskStepRepository.countByTaskId(taskId),
            createdAt = clock.instant()
        )
        val savedStep = taskStepRepository.save(step)
        syncWorkingMemory(taskId)
        return savedStep
    }

    fun listSteps(taskId: TaskId): List<TaskStep> = taskStepRepository.findByTaskId(taskId)

    suspend fun completeStep(stepId: String): TaskStep {
        val typedStepId = TaskStepId(stepId)
        val step = taskStepRepository.findById(typedStepId)
            ?: throw IllegalStateException("Step not found: $stepId")
        val completedStep = taskStepRepository.save(step.markCompleted())
        syncWorkingMemory(completedStep.taskId)
        return completedStep
    }

    private suspend fun syncWorkingMemory(taskId: TaskId) {
        memoryService.switchToTaskLevel(taskId)
    }
}
