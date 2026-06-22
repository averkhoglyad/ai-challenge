package io.averkhogliad.ai.challenge.week2.cli.commands

import io.averkhogliad.ai.challenge.week2.domain.model.TaskId

/**
 * Типизированное представление пользовательской команды.
 *
 * Использует sealed interface для обеспечения типобезопасности и
 * исчерпывающей обработки в when-выражениях (exhaustive when).
 *
 * Команды разделены на три категории:
 * - Глобальные: Help, Back, Quit, SelectTask
 * - Управление параметрами LLM: SetTemperature, SetMaxTokens, SetStopSequences и др.
 * - Пользовательский ввод: UserInput, Unknown
 * - Управление задачами todo-менеджера: AddTask, ListTasks, EditTask, DropTask, OpenTask, CloseTask, CancelTask
 */
sealed interface Command {
    // ═══════════════════════════════════════════════════════════════
    // Глобальные команды (доступны всегда)
    // ═══════════════════════════════════════════════════════════════

    /** Показать справку по командам */
    data object Help : Command

    /** Вернуться к выбору задачи */
    data object Back : Command

    /** Выйти из приложения */
    data object Quit : Command

    /** Выбрать задачу по номеру (1-based) */
    data class SelectTask(val taskId: Int) : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды управления параметрами LLM
    // ═══════════════════════════════════════════════════════════════

    /** Установить температуру (0.0–2.0) */
    data class SetTemperature(val value: Double) : Command

    /** Установить максимальное количество токенов */
    data class SetMaxTokens(val value: Int) : Command

    /** Установить стоп-последовательности */
    data class SetStopSequences(val values: List<String>) : Command

    /** Сбросить все параметры к значениям по умолчанию */
    data object ResetParameters : Command

    /** Показать текущие параметры */
    data object ShowParameters : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды управления диалогами (для Task 2)
    // ═══════════════════════════════════════════════════════════════

    /** Создать новый диалог с указанным заголовком */
    data class NewDialog(val title: String) : Command

    /** Показать список всех диалогов */
    data object ListDialogs : Command

    /** Удалить диалог по ID */
    data class DeleteDialog(val id: String) : Command

    /** Переключиться на диалог по ID */
    data class SwitchDialog(val id: String) : Command

    /** Показать историю сообщений диалога по ID (опционально — текущий диалог) */
    data class ShowHistory(val id: String? = null) : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды управления сжатием контекста (для Task 4)
    // ═══════════════════════════════════════════════════════════════

    /** Включить или выключить сжатие контекста */
    data class SetCompressionEnabled(val enabled: Boolean) : Command

    /** Установить размер скользящего окна */
    data class SetCompressionWindow(val size: Int) : Command

    /** Установить размер блока для суммаризации */
    data class SetCompressionBlock(val size: Int) : Command

    /** Показать текущий статус сжатия */
    data object ShowCompressionStatus : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды управления стратегиями контекста (для Task 5)
    // ═══════════════════════════════════════════════════════════════

    /** Показать меню выбора стратегии */
    data object ShowStrategyMenu : Command

    /** Переключить стратегию по индексу (1-based) */
    data class SwitchStrategy(val index: Int) : Command

    /** Показать текущую стратегию */
    data object ShowCurrentStrategy : Command

    /** Создать новую ветку (для Branching стратегии) */
    data class CreateBranch(val name: String) : Command

    /** Переключиться на ветку (для Branching стратегии) */
    data class SwitchBranch(val name: String) : Command

    /** Показать список веток (для Branching стратегии) */
    data object ListBranches : Command

    /** Создать чекпоинт (для Branching стратегии) */
    data object CreateCheckpoint : Command

    /** Показать список чекпоинтов (для Branching стратегии) */
    data object ListCheckpoints : Command

    /** Показать список фактов (для Sticky Facts стратегии) */
    data object ListFacts : Command

    /** Очистить все факты (для Sticky Facts стратегии) */
    data object ClearFacts : Command

    /** Добавить факт вручную (для Sticky Facts стратегии) */
    data class AddFact(val key: String, val value: String) : Command

    /** Удалить факт (для Sticky Facts стратегии) */
    data class RemoveFact(val key: String) : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды управления задачами todo-менеджера
    // ═══════════════════════════════════════════════════════════════

    /** Добавить новую задачу с указанным заголовком */
    data class AddTask(val title: String) : Command

    /** Показать список всех задач */
    data object ListTasks : Command

    /**
     * Редактировать задачу.
     * @param id ID задачи (null для контекстной команды — редактировать текущую задачу)
     * @param title новый заголовок задачи
     */
    data class EditTask(val id: TaskId?, val title: String) : Command

    /**
     * Удалить задачу.
     * @param id ID задачи (null для контекстной команды — удалить текущую задачу)
     */
    data class DropTask(val id: TaskId?) : Command

    /**
     * Открыть задачу (перевести в статус IN_PROGRESS).
     * @param id ID задачи (обязательный параметр)
     */
    data class OpenTask(val id: TaskId) : Command

    /**
     * Закрыть задачу (перевести в статус DONE).
     * @param id ID задачи (null для контекстной команды — закрыть текущую задачу)
     */
    data class CloseTask(val id: TaskId?) : Command

    /**
     * Отменить задачу (перевести в статус CANCELLED).
     * @param id ID задачи (null для контекстной команды — отменить текущую задачу)
     */
    data class CancelTask(val id: TaskId?) : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды управления шагами задач
    // ═══════════════════════════════════════════════════════════════

    /**
     * Добавить шаг к текущей открытой задаче.
     * Требует открытой задачи (SessionLevel.TASK).
     */
    data class AddStep(val text: String) : Command

    /** Показать список шагов текущей задачи */
    data object ListSteps : Command

    /**
     * Отметить шаг выполненным.
     * @param stepId идентификатор шага
     */
    data class CompleteStep(val stepId: String) : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды управления памятью (STM)
    // ═══════════════════════════════════════════════════════════════

    /** Очистить STM текущей сессии */
    data object ClearMemory : Command

    /** Показать состояние памяти текущей сессии */
    data object ShowStatus : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды управления долговременной памятью (LTM)
    // ═══════════════════════════════════════════════════════════════

    /** Сохранить факт в LTM: `:ctx-save <content>` */
    data class SaveFact(val content: String) : Command

    /** Показать список всех фактов LTM: `:ctx-list` */
    data object ListLtmFacts : Command

    /** Удалить факт из LTM: `:ctx-forget <factId>` */
    data class ForgetFact(val factId: String) : Command

    /** Полнотекстовый поиск фактов в LTM: `:ctx-search <query>` */
    data class SearchFacts(val query: String) : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды интеграции с LLM
    // ═══════════════════════════════════════════════════════════════

    /**
     * Запросить у ассистента план шагов по решению задачи.
     * Команда `:plan <title> [description]`.
     */
    data class PlanSteps(val title: String, val description: String? = null) : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды управления профилями пользователя (PM)
    // ═══════════════════════════════════════════════════════════════

    /** Создать новый профиль: `:profile-new <name>` */
    data class ProfileNew(val name: String) : Command

    /** Показать список всех профилей: `:profile-list` */
    data object ProfileList : Command

    /** Активировать профиль по имени: `:profile-use <name>` (или `none` для деактивации) */
    data class ProfileUse(val name: String) : Command

    /** Редактировать профиль: `:profile-edit <name>` */
    data class ProfileEdit(val name: String) : Command

    /** Удалить профиль: `:profile-delete <name>` */
    data class ProfileDelete(val name: String) : Command

    /** Показать содержимое профиля: `:profile-show [name]` */
    data class ProfileShow(val name: String? = null) : Command

    // ═══════════════════════════════════════════════════════════════
    // Пользовательский ввод
    // ═══════════════════════════════════════════════════════════════

    /** Пользователь ввёл текст промпта */
    data class UserInput(val text: String) : Command

    /** Неизвестная команда (начинается с ':' но не распознана) */
    data class Unknown(val raw: String) : Command
}
