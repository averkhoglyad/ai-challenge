package io.averkhogliad.ai.challenge.week3.cli.domain.model

/**
 * Модель рабочей памяти (Working Memory) для текущей сессии диалога.
 *
 * ## Архитектурная роль
 * - **Domain Model** — модель для управления текущим контекстом диалога
 * - **Immutable** — все изменения возвращают новый экземпляр
 *
 * ## Свойства
 * - [sessionId] — идентификатор сессии
 * - [currentMessages] — текущие сообщения в рабочей памяти
 * - [summary] — опциональная свёртка предыдущих сообщений (для сжатия контекста)
 * - [steps] — шаги текущей задачи для отслеживания прогресса
 *
 * ## Использование
 * Используется для управления краткосрочной памятью диалога. Когда количество
 * сообщений превышает лимит, старые сообщения могут быть свёрнуты в [summary].
 */
data class WorkingMemory(
    val sessionId: SessionId,
    val currentMessages: List<Message>,
    val summary: String?,
    val steps: List<TaskStep> = emptyList(),
    val taskDescription: String? = null
) {
    init {
        require(currentMessages.all { it.sessionId == sessionId }) {
            "All messages must belong to the same session"
        }
    }

    /**
     * Добавляет сообщение в рабочую память.
     *
     * @param message сообщение для добавления
     * @return новая копия рабочей памяти с добавленным сообщением
     */
    fun addMessage(message: Message): WorkingMemory {
        require(message.sessionId == sessionId) {
            "Message sessionId must match working memory session id"
        }
        return copy(currentMessages = currentMessages + message)
    }

    /**
     * Обновляет свёртку сообщений.
     *
     * @param newSummary новая свёртка
     * @return новая копия рабочей памяти с обновлённой свёрткой и очищенными сообщениями
     */
    fun updateSummary(newSummary: String): WorkingMemory = copy(
        summary = newSummary,
        currentMessages = emptyList()
    )

    /**
     * Обновляет список шагов задачи в рабочей памяти.
     *
     * @param newSteps новый список шагов
     * @return новая копия рабочей памяти с обновлёнными шагами
     */
    fun updateSteps(newSteps: List<TaskStep>): WorkingMemory = copy(steps = newSteps)

    /**
     * Формирует контекст для промпта LLM, включая свёртку, шаги задачи
     * и текущие сообщения диалога.
     *
     * Шаги отображаются в формате:
     * ```
     * Steps:
     * 1. [x] Step text (completed)
     * 2. [ ] Step text (pending)
     * ```
     *
     * @return строковое представление контекста для включения в промпт
     */
    fun toPromptContext(): String = buildString {
        if (taskDescription != null) {
            appendLine("Task Description:")
            appendLine(taskDescription)
            appendLine()
        }

        if (summary != null) {
            appendLine("Context Summary:")
            appendLine(summary)
            appendLine()
        }

        if (steps.isNotEmpty()) {
            appendLine("Steps:")
            steps.sortedBy { it.order }.forEachIndexed { index, step ->
                val marker = if (step.isCompleted) "[x]" else "[ ]"
                appendLine("${index + 1}. $marker ${step.text}")
            }
            appendLine()
        }

        if (currentMessages.isNotEmpty()) {
            appendLine("Recent Messages:")
            currentMessages.forEach { message ->
                appendLine("[${message.role}] ${message.content}")
            }
        }
    }

    /**
     * Создаёт новую рабочую память для указанной сессии.
     *
     * @param sessionId идентификатор сессии
     * @return новый экземпляр [WorkingMemory]
     */
    companion object {
        fun create(sessionId: SessionId): WorkingMemory = WorkingMemory(
            sessionId = sessionId,
            currentMessages = emptyList(),
            summary = null
        )

        /**
         * Создаёт рабочую память для уровня задачи с указанными шагами.
         *
         * @param sessionId идентификатор сессии
         * @param steps список шагов задачи (по умолчанию пустой)
         * @param taskDescription описание задачи (опционально)
         * @return новый экземпляр [WorkingMemory]
         */
        fun forTaskLevel(
            sessionId: SessionId,
            steps: List<TaskStep> = emptyList(),
            taskDescription: String? = null
        ): WorkingMemory = WorkingMemory(
            sessionId = sessionId,
            currentMessages = emptyList(),
            summary = null,
            steps = steps,
            taskDescription = taskDescription
        )
    }
}
