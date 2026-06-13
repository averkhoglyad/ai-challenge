package io.averkhogliad.ai.challenge.week1.application

import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.model.DialogSummary
import io.averkhogliad.ai.challenge.week1.domain.service.DialogRepository
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort
import java.util.*

/**
 * Application service для управления диалогами.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация domain-сервисов
 * - **Не зависит от UI** — не содержит CLI/Mordant
 * - **Координирует** операции с диалогами через [DialogRepository]
 *
 * ## Функциональность
 * - Создание новых диалогов
 * - Загрузка существующих диалогов
 * - Список всех диалогов
 * - Удаление диалогов
 * - Продолжение диалога с отправкой сообщения в LLM
 *
 * @property repository репозиторий для персистентного хранения диалогов
 */
class DialogManager(
    private val repository: DialogRepository
) {
    /**
     * Создаёт новый диалог с указанным заголовком.
     *
     * @param title название диалога
     * @return созданный диалог
     */
    suspend fun createNewDialog(title: String): Dialog {
        val id = DialogId(UUID.randomUUID().toString())
        val dialog = Dialog.create(id, title)
        repository.save(dialog)
        return dialog
    }

    /**
     * Загружает диалог по идентификатору.
     *
     * @param id идентификатор диалога
     * @return найденный диалог или null, если не найден
     */
    suspend fun loadDialog(id: DialogId): Dialog? {
        return repository.findById(id)
    }

    /**
     * Возвращает список всех диалогов (краткое представление).
     *
     * @return список кратких представлений диалогов, отсортированный по updatedAt DESC
     */
    suspend fun listDialogs(): List<DialogSummary> {
        return repository.findAll()
    }

    /**
     * Удаляет диалог по идентификатору.
     *
     * @param id идентификатор диалога для удаления
     */
    suspend fun deleteDialog(id: DialogId) {
        repository.delete(id)
    }

    /**
     * Продолжает диалог: добавляет сообщение пользователя, отправляет в LLM,
     * сохраняет ответ ассистента.
     *
     * @param id идентификатор диалога
     * @param userMessage сообщение пользователя
     * @param llmPort порт для взаимодействия с LLM
     * @param systemPrompt опциональный system prompt
     * @param config конфигурация выполнения
     * @return результат выполнения LLM
     */
    suspend fun continueDialog(
        id: DialogId,
        userMessage: String,
        llmPort: LlmPort,
        systemPrompt: String? = null,
        config: TaskExecutionConfig = TaskExecutionConfig()
    ): TaskResult {
        // Загружаем диалог
        val dialog = repository.findById(id)
            ?: return TaskResult.Error("Dialog not found: ${id.value}")

        // Добавляем сообщение пользователя
        var updatedDialog = dialog.addUserMessage(userMessage)

        // Формируем список сообщений для LLM
        val messages = buildList {
            if (systemPrompt != null) {
                add(io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage.system(systemPrompt))
            }
            addAll(updatedDialog.messages)
        }

        // Отправляем в LLM
        val result = try {
            llmPort.chatWithMessages(messages, config)
        } catch (e: Exception) {
            // Сохраняем диалог с сообщением пользователя даже при ошибке
            repository.save(updatedDialog)
            return TaskResult.Error("LLM request failed: ${e.message}", e)
        }

        // Добавляем ответ ассистента (если успешно)
        when (result) {
            is TaskResult.Success -> {
                updatedDialog = updatedDialog.addAssistantMessage(result.content)
                repository.save(updatedDialog)
            }

            is TaskResult.Partial -> {
                updatedDialog = updatedDialog.addAssistantMessage(result.content)
                repository.save(updatedDialog)
            }

            is TaskResult.Error -> {
                // Сохраняем диалог с сообщением пользователя даже при ошибке
                repository.save(updatedDialog)
            }
        }

        return result
    }
}
