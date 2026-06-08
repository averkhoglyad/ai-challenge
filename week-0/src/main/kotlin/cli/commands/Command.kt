package io.averkhogliad.ai.challenge.week0.cli.commands

import io.averkhogliad.ai.challenge.week0.domain.config.Task3Mode

/**
 * Типизированное представление пользовательской команды.
 *
 * Использует sealed interface для обеспечения типобезопасности и
 * исчерпывающей обработки в when-выражениях (exhaustive when).
 *
 * Команды разделены на три категории:
 * - Глобальные: Help, Back, Quit, SelectTask
 * - Специфичные для задач: SetTemperature, SetMaxTokens, SetStopSequences и др.
 * - Пользовательский ввод: UserInput, Unknown
 *
 * На уровне парсинга строковые литералы "on"/"off", "direct"/"experts"
 * преобразуются в типизированные значения (Boolean, Task3Mode).
 * Доменная логика никогда не оперирует строками с ограниченным множеством значений.
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
    // Команды управления параметрами LLM (Task2, Task4, Task5)
    // ═══════════════════════════════════════════════════════════════

    /** Установить температуру (0.0–2.0) */
    data class SetTemperature(val value: Double) : Command

    /** Установить максимальное количество токенов */
    data class SetMaxTokens(val value: Int) : Command

    /** Установить стоп-последовательности (Task2) */
    data class SetStopSequences(val values: List<String>) : Command

    /** Сбросить все параметры к значениям по умолчанию */
    data object ResetParameters : Command

    /** Показать текущие параметры */
    data object ShowParameters : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды промпт-инжиниринга (Task3)
    // ═══════════════════════════════════════════════════════════════

    /** Установить режим промпт-инжиниринга */
    data class SetMode(val mode: Task3Mode) : Command

    /** Включить/выключить пошаговый режим (step-by-step reasoning) */
    data class SetStep(val enabled: Boolean) : Command

    /** Включить/выключить мета-анализ */
    data class SetMeta(val enabled: Boolean) : Command

    /** Установить роль для LLM */
    data class SetRole(val role: String) : Command

    /** Установить список экспертов */
    data class SetExperts(val experts: List<String>) : Command

    /** Переключить режим суммаризации */
    data class ToggleSummary(val value: Boolean) : Command

    /** Показать текущую конфигурацию */
    data object ShowConfig : Command

    // ═══════════════════════════════════════════════════════════════
    // Команды бенчмарка (Task5)
    // ═══════════════════════════════════════════════════════════════

    /** Установить список моделей по индексам (1-based) */
    data class SetModels(val modelIndices: List<Int>) : Command

    /** Показать список доступных моделей */
    data object ShowModels : Command

    // ═══════════════════════════════════════════════════════════════
    // Пользовательский ввод
    // ═══════════════════════════════════════════════════════════════

    /** Пользователь ввёл текст промпта */
    data class UserInput(val text: String) : Command

    /** Неизвестная команда (начинается с ':' но не распознана) */
    data class Unknown(val raw: String) : Command
}
