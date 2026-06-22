package io.averkhogliad.ai.challenge.week2.application

import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week2.domain.service.ProfileRepository
import io.averkhogliad.ai.challenge.week2.domain.service.PromptBuilder

class DialogService(
    private val llmPort: LlmPort? = null,
    private val memoryService: MemoryService,
    private val promptBuilder: PromptBuilder,
    private val taskExecutionConfig: TaskExecutionConfig = TaskExecutionConfig(),
    private val profileRepository: ProfileRepository? = null,  // доступ к активному профилю для встраивания в промпт
    private val invariantService: InvariantService? = null  // NEW: доступ к инвариантам для встраивания в промпт
) {
    suspend fun chat(
        userInput: String,
        level: SessionLevel,
        taskId: TaskId? = null
    ): TaskResult {
        if (llmPort == null) {
            return TaskResult.Error("LLM не настроен. Добавьте API-ключ в конфигурацию.")
        }
        return try {
            val memoryContext = memoryService.getFullMemoryContext(
                level = level,
                taskId = taskId,
                userQuery = userInput,
                factSearchLimit = 5
            )
            // NEW: получаем активный профиль для встраивания в промпт
            val activeProfile = profileRepository?.findActive()
            // NEW: получаем активные инварианты для встраивания в промпт
            val invariants = invariantService?.list() ?: emptyList()
            val chatMessages = promptBuilder.buildChatMessages(
                workingMemory = memoryContext.workingMemory,
                relevantFacts = memoryContext.relevantFacts,
                recentMessages = memoryContext.recentMessages,
                userInput = userInput,
                profile = activeProfile,  // NEW: передача активного профиля
                invariants = invariants  // NEW: передача инвариантов
            )
            memoryService.saveUserMessage(level, taskId, userInput)
            val result = llmPort.chatWithMessages(chatMessages, taskExecutionConfig)
            if (result is TaskResult.Success) {
                memoryService.saveAssistantMessage(level, taskId, result.content)
            }
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TaskResult.Error("Error LLM: ${e.message}", e)
        }
    }

    suspend fun planSteps(
        taskTitle: String,
        taskDescription: String? = null,
        level: SessionLevel,
        taskId: TaskId? = null
    ): TaskResult {
        if (llmPort == null) {
            return TaskResult.Error("LLM не настроен. Добавьте API-ключ в конфигурацию.")
        }
        return try {
            val memoryContext = memoryService.getFullMemoryContext(
                level = level,
                taskId = taskId,
                userQuery = taskTitle,
                factSearchLimit = 3
            )
            // NEW: получаем активные инварианты для встраивания в промпт планирования
            val invariants = invariantService?.list() ?: emptyList()
            val planPrompt = promptBuilder.buildPlanPrompt(
                taskTitle = taskTitle,
                taskDescription = taskDescription,
                workingMemory = memoryContext.workingMemory,
                relevantFacts = memoryContext.relevantFacts,
                invariants = invariants  // NEW: передача инвариантов
            )
            llmPort.chat(Prompt(planPrompt), taskExecutionConfig)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TaskResult.Error("Error plan: ${e.message}", e)
        }
    }
}
