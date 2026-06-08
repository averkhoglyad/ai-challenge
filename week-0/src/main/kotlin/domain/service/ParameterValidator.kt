package io.averkhogliad.ai.challenge.week0.domain.service

/**
 * Domain-сервис валидации параметров.
 *
 * Набор чистых функций для проверки корректности параметров генерации.
 * Не зависит от внешних библиотек и infrastructure.
 * Все методы возвращают [Result] — либо успех с валидным значением, либо ошибку.
 *
 * Бизнес-правила валидации:
 * - Temperature: 0.0..2.0
 * - MaxTokens: 1..128000
 * - StopSequences: максимум 4 элемента
 */
object ParameterValidator {

    /** Константы допустимых диапазонов (см. [io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig]) */
    private const val TEMPERATURE_MIN = 0.0
    private const val TEMPERATURE_MAX = 2.0
    private const val MAX_TOKENS_MIN = 1
    private const val MAX_TOKENS_MAX = 128_000
    private const val MAX_STOP_SEQUENCES = 4

    /**
     * Валидирует значение temperature.
     *
     * @param value значение для проверки
     * @return [Result.success] с валидным значением, [Result.failure] с ошибкой валидации
     *
     * Пример:
     * ```
     * validateTemperature(0.7).isSuccess  // true
     * validateTemperature(3.0).isSuccess  // false
     * validateTemperature(-0.1).isSuccess // false
     * ```
     */
    fun validateTemperature(value: Double): Result<Double> {
        return if (value in TEMPERATURE_MIN..TEMPERATURE_MAX) {
            Result.success(value)
        } else {
            Result.failure(
                IllegalArgumentException(
                    "Temperature must be in $TEMPERATURE_MIN..$TEMPERATURE_MAX, got $value"
                )
            )
        }
    }

    /**
     * Валидирует значение maxTokens.
     *
     * @param value значение для проверки
     * @return [Result.success] с валидным значением, [Result.failure] с ошибкой валидации
     *
     * Пример:
     * ```
     * validateMaxTokens(500).isSuccess      // true
     * validateMaxTokens(0).isSuccess        // false
     * validateMaxTokens(200_000).isSuccess  // false
     * ```
     */
    fun validateMaxTokens(value: Int): Result<Int> {
        return if (value in MAX_TOKENS_MIN..MAX_TOKENS_MAX) {
            Result.success(value)
        } else {
            Result.failure(
                IllegalArgumentException(
                    "MaxTokens must be in $MAX_TOKENS_MIN..$MAX_TOKENS_MAX, got $value"
                )
            )
        }
    }

    /**
     * Валидирует список stop sequences.
     *
     * @param values список стоп-последовательностей для проверки
     * @return [Result.success] с валидным списком, [Result.failure] с ошибкой валидации
     *
     * Пример:
     * ```
     * validateStopSequences(listOf("END")).isSuccess           // true
     * validateStopSequences(emptyList()).isSuccess             // true
     * validateStopSequences(listOf("a", "b", "c", "d", "e")).isSuccess  // false (>4)
     * ```
     */
    fun validateStopSequences(values: List<String>): Result<List<String>> {
        return if (values.size <= MAX_STOP_SEQUENCES) {
            Result.success(values)
        } else {
            Result.failure(
                IllegalArgumentException(
                    "StopSequences cannot exceed $MAX_STOP_SEQUENCES, got ${values.size}"
                )
            )
        }
    }

    /**
     * Валидирует список значений temperature (для бенчмарка).
     *
     * @param values список значений для проверки
     * @return [Result.success] с валидным списком, [Result.failure] с ошибкой валидации
     */
    fun validateTemperatures(values: List<Double>): Result<List<Double>> {
        if (values.isEmpty()) {
            return Result.failure(IllegalArgumentException("temperatures cannot be empty"))
        }
        val invalid = values.filter { it !in TEMPERATURE_MIN..TEMPERATURE_MAX }
        if (invalid.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "all temperatures must be in $TEMPERATURE_MIN..$TEMPERATURE_MAX, invalid: $invalid"
                )
            )
        }
        return Result.success(values)
    }

    /**
     * Валидирует, что имя (роль, эксперт) не пустое.
     *
     * @param name имя для проверки
     * @return [Result.success] с валидным именем, [Result.failure] если имя пустое
     */
    fun validateName(name: String): Result<String> {
        return if (name.isNotBlank()) {
            Result.success(name)
        } else {
            Result.failure(IllegalArgumentException("Name cannot be blank"))
        }
    }
}
