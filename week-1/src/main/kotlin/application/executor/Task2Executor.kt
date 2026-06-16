package io.averkhogliad.ai.challenge.week1.application.executor

import io.averkhogliad.ai.challenge.week1.application.DialogManager
import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskId
import io.averkhogliad.ai.challenge.week1.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.service.ConversationalAgent

/**
 * Интерфейс для доступа к [DialogManager] без прямого приведения типов.
 * Позволяет получать менеджер диалогов из executor'а через единый контракт.
 */
interface DialogManagerAccessor {
    /**
     * Возвращает [DialogManager] для доступа к операциям с диалогами.
     */
    fun getDialogManager(): DialogManager

    /**
     * Возвращает ID текущего активного диалога.
     *
     * @return ID текущего диалога или null, если диалог не выбран
     */
    fun getCurrentDialogId(): DialogId?

    /**
     * Устанавливает текущий активный диалог.
     *
     * @param id идентификатор диалога
     */
    fun setCurrentDialog(id: DialogId)

    /**
     * Создаёт новый диалог и делает его активным.
     *
     * @param title название диалога
     * @return ID созданного диалога
     */
    suspend fun createNewDialog(title: String): DialogId
}

/**
 * Executor для Task 2: персистентные диалоги с историей сообщений.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация domain-сервисов
 * - **Хранит состояние** — управляет текущим активным диалогом ([currentDialogId])
 * - **Не зависит от UI** — не содержит CLI/Mordant
 * - **Делегирует бизнес-логику** [ConversationalAgent] и [DialogManager]
 *
 * ## Функциональность
 * - Автоматическое создание диалога при первом запросе
 * - Сохранение истории сообщений в SQLite
 * - Поддержка множественных изолированных диалогов
 * - Переключение между диалогами
 *
 * ## Управление диалогами
 * - [setCurrentDialog] — переключает активный диалог
 * - [createNewDialog] — создаёт новый диалог
 * - [getCurrentDialogId] — возвращает ID текущего диалога
 *
 * @property agent агент с поддержкой персистентных диалогов
 * @property dialogManager менеджер для управления диалогами
 */
class Task2Executor(
    private val agent: ConversationalAgent,
    private val dialogManager: DialogManager
) : TaskExecutor, DialogManagerAccessor {

    override val taskId: TaskId = TaskId(2)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 2: персистентные диалоги с историей",
        description = "Агент с памятью диалога, сохраняющий историю в SQLite. " +
                "Поддерживает множественные изолированные диалоги.",
        availableCommands = listOf(":new", ":list", ":history", ":delete", ":switch")
    )

    /** ID текущего активного диалога (null — диалог не выбран) */
    private var currentDialogId: DialogId? = null

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return try {
            // Если диалог не выбран, создаём новый автоматически
            val dialogId = currentDialogId ?: createNewDialog(prompt.value.take(30).ifBlank { "New Dialog" })

            // Обрабатываем запрос через ConversationalAgent
            agent.process(prompt, config, dialogId)
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 2 execution failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Устанавливает текущий активный диалог.
     *
     * @param id идентификатор диалога
     */
    override fun setCurrentDialog(id: DialogId) {
        currentDialogId = id
    }

    /**
     * Создаёт новый диалог и делает его активным.
     *
     * @param title название диалога
     * @return ID созданного диалога
     */
    override suspend fun createNewDialog(title: String): DialogId {
        val dialog = dialogManager.createNewDialog(title)
        currentDialogId = dialog.id
        return dialog.id
    }

    /**
     * Возвращает ID текущего активного диалога.
     *
     * @return ID текущего диалога или null, если диалог не выбран
     */
    override fun getCurrentDialogId(): DialogId? {
        return currentDialogId
    }

    /**
     * Возвращает [DialogManager] для доступа к операциям с диалогами.
     */
    override fun getDialogManager(): DialogManager = dialogManager
}
