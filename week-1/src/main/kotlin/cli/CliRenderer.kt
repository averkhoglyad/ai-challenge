package io.averkhogliad.ai.challenge.week1.cli

import io.averkhogliad.ai.challenge.week1.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week1.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogSummary

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
     * - На этапе выбора задачи: "Выберите задачу (номер, 0=выход, :help=помощь):"
     * - Внутри задачи: "prompt>"
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
     * Отображает приветственное сообщение при запуске.
     */
    fun renderWelcome()

    /**
     * Отображает сообщение при выходе.
     */
    fun renderGoodbye()

    /**
     * Отображает информацию о запросе перед отправкой в LLM.
     *
     * @param prompt текст промпта
     * @param config конфигурация выполнения (temperature, maxTokens, modelId)
     */
    fun renderRequestInfo(prompt: String, config: io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig)

    /**
     * Отображает список диалогов.
     *
     * @param dialogs список кратких представлений диалогов
     */
    fun renderDialogList(dialogs: List<DialogSummary>)

    /**
     * Отображает историю сообщений диалога.
     *
     * @param dialog полный диалог с историей сообщений
     */
    fun renderDialogHistory(dialog: Dialog)

    /**
     * Отображает информацию о текущем активном диалоге.
     *
     * @param dialogId ID текущего диалога (null если не выбран)
     */
    fun renderCurrentDialogInfo(dialogId: io.averkhogliad.ai.challenge.week1.domain.model.DialogId?)
}
