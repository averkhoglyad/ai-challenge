package io.averkhogliad.ai.challenge.week1.cli.commands

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
    // Пользовательский ввод
    // ═══════════════════════════════════════════════════════════════

    /** Пользователь ввёл текст промпта */
    data class UserInput(val text: String) : Command

    /** Неизвестная команда (начинается с ':' но не распознана) */
    data class Unknown(val raw: String) : Command
}
