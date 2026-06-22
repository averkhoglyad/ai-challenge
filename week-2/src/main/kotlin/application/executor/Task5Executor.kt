package io.averkhogliad.ai.challenge.week2.application.executor

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskId
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.Profile
import io.averkhogliad.ai.challenge.week2.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService

/**
 * Executor для Task 5: CLI-ассистент с контролируемыми переходами состояний (копия Task 4).
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация диалога с LLM через [DialogService]
 * - **Не зависит от UI** (CLI, Mordant)
 * - **Делегирует бизнес-логику** [DialogService], [MemoryService] и [ProfileService]
 *
 * ## Функциональность
 * - Диалоговый ассистент с трёхуровневой моделью памяти (STM/WM/LTM)
 * - Поддерживает управление задачами, шагами, фактами и общение с LLM
 * - Поддерживает возможность генерить план выполнения
 * - Поддерживает контролируемые переходы состояний FSM (Sprint 5)
 * - Идентичен Task 4, создан для демонстрации FSM-переходов
 *
 * @param dialogService сервис диалога с LLM
 * @param memoryService сервис управления памятью (не используется напрямую, передан для симметрии)
 * @param profileService сервис для управления профилями (композиция)
 */
class Task5Executor(
    private val dialogService: DialogService,
    private val memoryService: MemoryService,
    private val profileService: ProfileService
) : TaskExecutor {

    override val taskId: TaskId = TaskId(5)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 5: CLI-ассистент с FSM",
        description = "Диалоговый ассистент с трёхуровневой моделью памяти (STM/WM/LTM). " +
                "Поддерживает управление задачами, шагами, фактами, общение с LLM, " +
                "возможность генерить план выполнения и контролируемые переходы состояний FSM.",
        availableCommands = listOf(
            ":add <text>", ":list", ":edit <id> <text>", ":drop <id>",
            ":open <id>", ":close", ":cancel", ":back",
            ":step-add <text>", ":step-list", ":step-done <id>",
            ":ctx-save <text>", ":ctx-list", ":ctx-forget <id>",
            ":plan <title>", ":goto [state]", ":status", ":clear",
            ":temp <value>", ":maxtokens <n>", ":params"
        )
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        // Делегируем в DialogService.chat() — основную точку входа для общения
        return dialogService.chat(
            userInput = prompt.value,
            level = SessionLevel.TASK_LIST,
            taskId = null
        )
    }

    // ──── Profile management delegation ────

    /** Делегат в [ProfileService.handleCreateProfile] */
    suspend fun handleCreateProfile(name: String, description: String, instructions: String): Profile =
        profileService.handleCreateProfile(name, description, instructions)

    /** Делегат в [ProfileService.handleListProfiles] */
    suspend fun handleListProfiles(): List<Profile> =
        profileService.handleListProfiles()

    /** Делегат в [ProfileService.handleActivateProfile] */
    suspend fun handleActivateProfile(id: ProfileId): Profile =
        profileService.handleActivateProfile(id)

    /** Делегат в [ProfileService.handleActivateByName] */
    suspend fun handleActivateByName(name: String): Profile =
        profileService.handleActivateByName(name)

    /** Делегат в [ProfileService.handleDeactivateProfile] */
    suspend fun handleDeactivateProfile() =
        profileService.handleDeactivateProfile()

    /** Делегат в [ProfileService.handleGetActiveProfile] */
    suspend fun handleGetActiveProfile(): Profile? =
        profileService.handleGetActiveProfile()

    /** Делегат в [ProfileService.handleEditProfile] */
    suspend fun handleEditProfile(
        name: String,
        newName: String?,
        newDescription: String?,
        newInstructions: String?
    ): Profile =
        profileService.handleEditProfile(name, newName, newDescription, newInstructions)

    /** Делегат в [ProfileService.handleDeleteProfile] */
    suspend fun handleDeleteProfile(name: String) =
        profileService.handleDeleteProfile(name)

    /** Делегат в [ProfileService.handleShowProfile] */
    suspend fun handleShowProfile(name: String?): Profile =
        profileService.handleShowProfile(name)
}
