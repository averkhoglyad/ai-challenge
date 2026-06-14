package io.averkhogliad.ai.challenge.week1.domain.model

import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import java.time.Instant

/**
 * Уникальный идентификатор ветки диалога.
 */
@JvmInline
value class BranchId(val value: String) {
    init {
        require(value.isNotBlank()) { "BranchId cannot be blank" }
    }
}

/**
 * Уникальный идентификатор чекпоинта.
 */
@JvmInline
value class CheckpointId(val value: String) {
    init {
        require(value.isNotBlank()) { "CheckpointId cannot be blank" }
    }
}

/**
 * Точка сохранения (checkpoint) в диалоге.
 *
 * Содержит снимок состояния диалога на определённый момент времени.
 * Используется стратегией Branching для создания веток.
 *
 * @property id уникальный идентификатор чекпоинта
 * @property dialogId идентификатор диалога
 * @property messageIndex индекс сообщения, на котором создан чекпоинт
 * @property messagesSnapshot снимок сообщений на момент создания
 * @property factsSnapshot снимок фактов на момент создания (для Sticky Facts)
 * @property createdAt время создания
 */
data class Checkpoint(
    val id: CheckpointId,
    val dialogId: DialogId,
    val messageIndex: Int,
    val messagesSnapshot: List<ChatMessage>,
    val factsSnapshot: Map<String, String> = emptyMap(),
    val createdAt: Instant = Instant.now()
) {
    init {
        require(messageIndex >= 0) { "messageIndex must be non-negative, got $messageIndex" }
    }
}

/**
 * Ветка диалога.
 *
 * Представляет независимую линию развития диалога от точки сохранения.
 *
 * @property id уникальный идентификатор ветки
 * @property name человекочитаемое имя ветки
 * @property dialogId идентификатор родительского диалога
 * @property parentCheckpointId идентификатор чекпоинта, от которого создана ветка (null для главной ветки)
 * @property messages сообщения в этой ветке
 * @property factsStore хранилище фактов для этой ветки (для Sticky Facts)
 * @property isActive флаг активной ветки
 * @property createdAt время создания
 */
data class DialogBranch(
    val id: BranchId,
    val name: String,
    val dialogId: DialogId,
    val parentCheckpointId: CheckpointId? = null,
    val messages: List<ChatMessage> = emptyList(),
    val factsStore: FactsStore = FactsStore(),
    val isActive: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "Branch name cannot be blank" }
    }

    /**
     * Добавляет сообщение в ветку.
     */
    fun addMessage(message: ChatMessage): DialogBranch =
        copy(messages = messages + message)

    /**
     * Обновляет хранилище фактов.
     */
    fun updateFacts(newFactsStore: FactsStore): DialogBranch =
        copy(factsStore = newFactsStore)

    /**
     * Активирует ветку.
     */
    fun activate(): DialogBranch = copy(isActive = true)

    /**
     * Деактивирует ветку.
     */
    fun deactivate(): DialogBranch = copy(isActive = false)

    companion object {
        /**
         * Создаёт новую ветку от чекпоинта.
         */
        fun createFromCheckpoint(
            id: BranchId,
            name: String,
            dialogId: DialogId,
            checkpoint: Checkpoint
        ): DialogBranch = DialogBranch(
            id = id,
            name = name,
            dialogId = dialogId,
            parentCheckpointId = checkpoint.id,
            messages = checkpoint.messagesSnapshot.toList(),
            factsStore = FactsStore(
                facts = checkpoint.factsSnapshot.mapValues { (key, value) ->
                    val (category, factName) = StickyFact.parseKey(key)
                    StickyFact(
                        key = key,
                        value = value,
                        category = category,
                        extractedAt = checkpoint.createdAt
                    )
                }
            )
        )

        /**
         * Создаёт главную ветку (без родительского чекпоинта).
         */
        fun createMain(dialogId: DialogId): DialogBranch = DialogBranch(
            id = BranchId("main"),
            name = "main",
            dialogId = dialogId,
            parentCheckpointId = null,
            isActive = true
        )
    }
}
