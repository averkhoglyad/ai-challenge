package io.averkhogliad.ai.challenge.week0.application.executor

import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig

/**
 * Базовый интерфейс для всех executor'ов задач (Application Layer).
 *
 * Каждый executor оркестрирует выполнение одной задачи (Task1-Task5),
 * делегируя бизнес-логику domain-сервисам ([LlmPort], [TemperatureService],
 * [PromptEngineeringService], [ModelBenchmarkService]) и преобразуя
 * результаты/ошибки в унифицированный [TaskResult].
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация, координация domain-сервисов
 * - **Не зависит** от UI (Mordant, CLI, Menu)
 * - **Не зависит** от infrastructure (LlmAdapter, ConfigAdapter)
 * - **Зависит только** от domain (port-интерфейсы, модели, конфиги)
 *
 * ## Почему такой интерфейс
 * - [taskId] и [metadata] предоставляют мета-информацию для UI/меню
 *   без необходимости знать конкретный класс executor'а
 * - [execute] принимает [Prompt] и [TaskExecutionConfig] — минимально
 *   необходимые параметры для выполнения любой задачи
 * - Возвращает [TaskResult] — sealed interface, гарантирующий
 *   исчерпывающую обработку успеха/ошибки/частичного результата
 * - suspend-функция для поддержки корутин (параллельные запросы в Task3-Task5)
 */
interface TaskExecutor {
    /** Уникальный идентификатор задачи */
    val taskId: TaskId

    /** Метаданные задачи (название, описание, доступные команды) */
    val metadata: TaskMetadata

    /**
     * Выполняет задачу с заданным промптом и конфигурацией.
     *
     * @param prompt пользовательский промпт
     * @param config конфигурация выполнения (temperature, maxTokens, modelId, etc.)
     * @return результат выполнения: [TaskResult.Success], [TaskResult.Error] или [TaskResult.Partial]
     */
    suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult
}
