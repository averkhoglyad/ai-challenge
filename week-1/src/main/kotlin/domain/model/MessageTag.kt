package io.averkhogliad.ai.challenge.week1.domain.model

import kotlinx.serialization.Serializable

/**
 * Универсальный маркер сообщения, не привязанный к конкретному алгоритму сжатия контекста.
 *
 * Каждое сообщение в диалоге может иметь ноль или более маркеров, описывающих
 * как оно было обработано стратегией управления контекстом.
 *
 * ## Расширяемость
 * Новые типы маркеров добавляются как реализации sealed interface без изменения
 * существующего кода рендеринга и хранения.
 */
@Serializable
sealed interface MessageTag {
    /** Стабильный ключ для сериализации (не зависит от языка/UI) */
    val key: String

    /** Emoji-символ для выразительного CLI-отображения */
    val emoji: String

    /** Удобочитаемая короткая метка */
    val label: String

    /** Полная отображаемая метка: emoji + label */
    val displayLabel: String get() = "$emoji $label"

    /** Компактная отображаемая метка: [emoji label] */
    val displayLabelCompact: String get() = "[$displayLabel]"

    /** Сжатие контекста — сообщение было суммаризировано стратегией сжатия */
    @Serializable
    data object Compressed : MessageTag {
        override val key: String = "compressed"
        override val emoji: String = "🗜️"
        override val label: String = "сжато"
    }

    /** Извлечение факта — сообщение инициировало извлечение sticky факта */
    @Serializable
    data object FactExtraction : MessageTag {
        override val key: String = "fact"
        override val emoji: String = "📌"
        override val label: String = "факт"
    }

    /** Точка ветвления — на этом сообщении была создана новая ветка диалога */
    @Serializable
    data object BranchPoint : MessageTag {
        override val key: String = "branch"
        override val emoji: String = "🌿"
        override val label: String = "бранч"
    }

    /** Чекпоинт/снапшот — на этом сообщении был создан checkpoint */
    @Serializable
    data object Checkpoint : MessageTag {
        override val key: String = "checkpoint"
        override val emoji: String = "🔖"
        override val label: String = "чекпоинт"
    }
}

/**
 * Сводная статистика по маркерам сообщений в диалоге.
 *
 * Используется в [DialogSummary] для отображения в списке диалогов.
 */
data class TagStats(
    val compressed: Int = 0,
    val factExtractions: Int = 0,
    val branchPoints: Int = 0,
    val checkpoints: Int = 0
) {
    companion object {
        /**
         * Вычисляет TagStats из карты маркеров диалога.
         */
        fun fromMessageTags(tags: Map<Int, Set<MessageTag>>): TagStats {
            var compressed = 0
            var facts = 0
            var branches = 0
            var checkpoints = 0

            for (tagSet in tags.values) {
                for (tag in tagSet) {
                    when (tag) {
                        MessageTag.Compressed -> compressed++
                        MessageTag.FactExtraction -> facts++
                        MessageTag.BranchPoint -> branches++
                        MessageTag.Checkpoint -> checkpoints++
                    }
                }
            }

            return TagStats(compressed, facts, branches, checkpoints)
        }

        fun empty(): TagStats = TagStats()
    }

    val isEmpty: Boolean get() = compressed == 0 && factExtractions == 0 && branchPoints == 0 && checkpoints == 0
}
