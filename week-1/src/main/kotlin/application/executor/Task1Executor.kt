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
 * Executor для Task 1: чат с сохранением истории диалогов в памяти.
 *
 * В отличие от изолированного SimpleAgent, Task 1 теперь поддерживает
 * множественные диалоги с хранением в
 * [InMemoryDialogRepository][io.averkhogliad.ai.challenge.week1.infrastructure.persistence.InMemoryDialogRepository].
 * История накапливается в памяти и сбрасывается при перезапуске приложения.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация domain-сервисов
 * - **Хранит состояние** — управляет текущим активным диалогом ([currentDialogId])
 * - **DialogManagerAccessor** — предоставляет команды управления диалогами CLI
 * - **Не зависит от UI** — не содержит CLI/Mordant
 * - **Делегирует бизнес-логику** [ConversationalAgent] и [DialogManager]
 *
 * ## Функциональность
 * - Автоматическое создание диалога при первом запросе
 * - Сохранение истории сообщений в in-memory хранилище
 * - Поддержка множественных изолированных диалогов
 * - Переключение между диалогами (`:switch`), создание новых (`:new`),
 *   просмотр списка (`:list`), удаление (`:delete`)
 *
 * ## Управление диалогами
 * - [setCurrentDialog] — переключает активный диалог
 * - [createNewDialog] — создаёт новый диалог
 * - [getCurrentDialogId] — возвращает ID текущего диалога
 *
 * @property agent агент с поддержкой персистентных диалогов
 * @property dialogManager менеджер для управления диалогами (in-memory)
 */
class Task1Executor(
    private val agent: ConversationalAgent,
    private val dialogManager: DialogManager
) : TaskExecutor, DialogManagerAccessor {

    override val taskId: TaskId = TaskId(1)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 1: чат с сохранением истории диалогов в памяти",
        description = "Агент с памятью диалога, сохраняющий историю в памяти. " +
                "Поддерживает множественные изолированные диалоги. " +
                "История сбрасывается при перезапуске приложения.",
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
                message = "Task 1 execution failed: ${e.message}",
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
