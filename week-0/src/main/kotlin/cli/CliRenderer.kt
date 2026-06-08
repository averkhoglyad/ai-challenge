package io.averkhogliad.ai.challenge.week0.cli

import io.averkhogliad.ai.challenge.week0.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week0.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week0.domain.TaskResult

/**
 * Интерфейс рендеринга CLI вывода (View).
 *
 * Отделяет логику отображения от обработки команд и бизнес-логики.
 * Позволяет легко подменять реализацию:
 * - [ConsoleCliRenderer] — вывод в консоль (System.out)
 * - Тестовые реализации — сбор вывода в буфер для проверки
 *
 * Не содержит бизнес-логики (это ответственность [CommandHandler] и executors).
 * Только форматирует и выводит данные.
 */
interface CliRenderer {

    /**
     * Отображает главное меню выбора задачи.
     *
     * @param executors список доступных executor'ов с метаданными задач
     */
    fun renderMenu(executors: List<TaskExecutor>)

    /**
     * Отображает заголовок задачи при входе в неё.
     *
     * @param metadata метаданные задачи (название, описание, доступные команды)
     */
    fun renderTaskHeader(metadata: TaskMetadata)

    /**
     * Отображает успешный или частичный результат выполнения задачи.
     *
     * @param result результат выполнения
     */
    fun renderResult(result: TaskResult)

    /**
     * Отображает сообщение об ошибке.
     *
     * @param message текст ошибки
     */
    fun renderError(message: String)

    /**
     * Отображает промпт для ввода пользователя.
     *
     * Формат промпта зависит от текущего состояния:
     * - На этапе выбора задачи: "Выберите задачу (1-5, 0=выход):"
     * - Внутри задачи: "Введите промпт (пустая строка = дефолтный, :help = помощь):"
     *
     * @param state текущее состояние CLI
     */
    fun renderPrompt(state: CliState)

    /**
     * Отображает справку по доступным командам.
     *
     * @param state текущее состояние CLI (для контекстно-зависимой справки)
     */
    fun renderHelp(state: CliState)

    /**
     * Отображает текущие параметры выполнения.
     *
     * @param state текущее состояние CLI
     */
    fun renderParameters(state: CliState)

    /**
     * Отображает конфигурацию Task3 (промпт-инжиниринг).
     *
     * @param state текущее состояние CLI
     */
    fun renderTask3Config(state: CliState)

    /**
     * Отображает список доступных моделей для Task5.
     *
     * @param models список идентификаторов моделей
     */
    fun renderAvailableModels(models: List<io.averkhogliad.ai.challenge.week0.domain.ModelId>)

    /**
     * Отображает приветственное сообщение при запуске.
     */
    fun renderWelcome()

    /**
     * Отображает сообщение при выходе.
     */
    fun renderGoodbye()
}
