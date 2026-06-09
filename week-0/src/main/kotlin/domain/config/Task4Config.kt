package io.averkhogliad.ai.challenge.week0.domain.config

/**
 * Immutable конфигурация Task4 (демонстрация влияния temperature на генерацию).
 *
 * Содержит параметры, специфичные для Task4:
 * список значений temperature для бенчмарка.
 *
 * В отличие от предыдущего подхода (temperatures в конструкторе [Task4Executor]),
 * [Task4Config] хранится внутри [TaskExecutionConfig] и передаётся
 * через [TaskExecutor.execute], что позволяет CLI-слою изменять
 * настройки Task4 без пересоздания executor'а.
 *
 * @property temperatures Список значений temperature для бенчмарка (каждое в диапазоне 0.0..2.0)
 */
data class Task4Config(
    val temperatures: List<Double> = DEFAULT_TEMPERATURES
) {
    init {
        require(temperatures.isNotEmpty()) {
            "temperatures cannot be empty"
        }
        require(temperatures.all { it in 0.0..2.0 }) {
            "all temperatures must be in 0.0..2.0, got $temperatures"
        }
    }

    companion object {
        /** Значения temperature по умолчанию. */
        val DEFAULT_TEMPERATURES = listOf(0.0, 0.7, 1.2)
    }
}
