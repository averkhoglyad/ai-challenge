package io.averkhogliad.ai.challenge.week1.domain.model

import java.time.Instant

/**
 * DTO для краткого представления диалога в списках.
 *
 * Содержит только необходимую информацию для отображения в UI:
 * идентификатор, название, количество сообщений и время обновления.
 * Не содержит полную историю сообщений для экономии памяти.
 *
 * ## Архитектурная роль
 * - **Read Model** — используется для отображения списков диалогов
 * - **Immutable** — все поля val, создаётся один раз
 * - **Lightweight** — минимальный объём данных для быстрого рендеринга
 *
 * @property id уникальный идентификатор диалога
 * @property title человекочитаемое название диалога
 * @property messageCount количество сообщений в диалоге
 * @property updatedAt время последнего обновления
 */
data class DialogSummary(
    val id: DialogId,
    val title: String,
    val messageCount: Int,
    val updatedAt: Instant,
    val accumulatedSummary: String? = null,
    val tagStats: TagStats = TagStats.empty()
) {
    init {
        require(title.isNotBlank()) { "Dialog title cannot be blank" }
        require(messageCount >= 0) { "Message count cannot be negative" }
    }

    /**
     * Количество сжатых сообщений — вычисляется из [tagStats].
     */
    val compressedMessageCount: Int get() = tagStats.compressed

    companion object {
        /**
         * Создаёт [DialogSummary] из полной модели [Dialog].
         *
         * @param dialog полная модель диалога
         * @return краткое представление диалога
         */
        fun fromDialog(dialog: Dialog): DialogSummary {
            return DialogSummary(
                id = dialog.id,
                title = dialog.title,
                messageCount = dialog.messageCount,
                updatedAt = dialog.updatedAt,
                accumulatedSummary = dialog.accumulatedSummary,
                tagStats = TagStats.fromMessageTags(dialog.messageTags)
            )
        }
    }
}
