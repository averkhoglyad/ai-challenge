package io.averkhogliad.ai.challenge.week4.cli.domain.model

import java.time.Instant

/**
 * Дельта изменения [TaskState] — результат LLM-извлечения из нового сообщения.
 *
 * ## Архитектурная роль
 * - **Sealed Interface** — исчерпывающий набор атомарных операций над [TaskState]
 * - **Command Pattern** — каждая дельта описывает одно изменение
 *
 * ## Варианты
 * - [SetGoal] — установить/заменить цель задачи
 * - [AddTerm] — добавить новый термин с определением
 * - [RemoveTerm] — удалить термин по имени
 * - [AddConstraint] — добавить ограничение
 * - [RemoveConstraint] — удалить ограничение по индексу
 * - [ResetAll] — сбросить всё состояние к пустому
 * - [NoChanges] — изменений нет
 * - [Composite] — композиция нескольких дельт
 */
sealed interface TaskStateDelta {

    /** Установить или заменить цель задачи. */
    data class SetGoal(val text: String) : TaskStateDelta

    /** Добавить новый термин с определением. */
    data class AddTerm(val name: String, val definition: String) : TaskStateDelta

    /** Удалить термин по имени. */
    data class RemoveTerm(val name: String) : TaskStateDelta

    /** Добавить ограничение. */
    data class AddConstraint(val text: String) : TaskStateDelta

    /** Удалить ограничение по индексу в списке. */
    data class RemoveConstraint(val index: Int) : TaskStateDelta

    /** Добавить уточнённый факт. */
    data class AddClarifiedFact(val text: String) : TaskStateDelta

    /** Удалить уточнённый факт по индексу. */
    data class RemoveClarifiedFact(val index: Int) : TaskStateDelta

    /** Сбросить всё состояние задачи к пустому. */
    data object ResetAll : TaskStateDelta

    /** Нет изменений. */
    data object NoChanges : TaskStateDelta

    /** Композиция нескольких дельт, применяемых последовательно. */
    data class Composite(val deltas: List<TaskStateDelta>) : TaskStateDelta
}

/**
 * Применяет [delta] к [state] и возвращает новый [TaskState].
 *
 * Чистая функция без побочных эффектов. Всегда возвращает новый экземпляр,
 * исходный [state] не изменяется.
 *
 * @param state текущее состояние задачи
 * @param delta дельта изменений
 * @return новое состояние задачи с применёнными изменениями
 */
fun TaskState.applyDelta(
    delta: TaskStateDelta,
    maxTerms: Int = Int.MAX_VALUE,
    maxConstraints: Int = Int.MAX_VALUE,
    maxClarifiedFacts: Int = Int.MAX_VALUE
): TaskState = when (delta) {
    is TaskStateDelta.SetGoal -> copy(
        goal = delta.text.ifBlank { null },
        lastUpdated = Instant.now()
    )

    is TaskStateDelta.AddTerm -> {
        if (definedTerms.size >= maxTerms) this
        else copy(
            definedTerms = definedTerms + (delta.name to delta.definition),
            lastUpdated = Instant.now()
        )
    }

    is TaskStateDelta.RemoveTerm -> copy(
        definedTerms = definedTerms.filter { it.first != delta.name },
        lastUpdated = Instant.now()
    )

    is TaskStateDelta.AddConstraint -> {
        if (constraints.size >= maxConstraints) this
        else copy(
            constraints = constraints + delta.text,
            lastUpdated = Instant.now()
        )
    }

    is TaskStateDelta.RemoveConstraint -> copy(
        constraints = constraints.filterIndexed { index, _ -> index != delta.index },
        lastUpdated = Instant.now()
    )

    is TaskStateDelta.AddClarifiedFact -> {
        if (clarifiedFacts.size >= maxClarifiedFacts) this
        else copy(
            clarifiedFacts = clarifiedFacts + delta.text,
            lastUpdated = Instant.now()
        )
    }

    is TaskStateDelta.RemoveClarifiedFact -> copy(
        clarifiedFacts = clarifiedFacts.filterIndexed { index, _ -> index != delta.index },
        lastUpdated = Instant.now()
    )

    is TaskStateDelta.ResetAll -> TaskState.EMPTY.copy(lastUpdated = Instant.now())

    is TaskStateDelta.NoChanges -> this

    is TaskStateDelta.Composite -> delta.deltas.fold(this) { acc, d ->
        acc.applyDelta(d, maxTerms, maxConstraints, maxClarifiedFacts)
    }
}
