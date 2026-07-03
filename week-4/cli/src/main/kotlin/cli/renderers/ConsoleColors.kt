package io.averkhogliad.ai.challenge.week4.cli.cli.renderers

/**
 * Общие ANSI-цвета для консольных рендереров.
 * Выделены в отдельный объект для переиспользования между специализированными рендерерами
 * и избежания дублирования констант.
 */
internal object ConsoleColors {
    const val RESET = "\u001B[0m"
    const val GREEN = "\u001B[32m"
    const val RED = "\u001B[31m"
    const val YELLOW = "\u001B[33m"
    const val CYAN = "\u001B[36m"
}
