package io.averkhogliad.ai.challenge.week0

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.LlmClient

/**
 * Контракт учебной задачи в модуле `week-0`.
 *
 * Реализация должна:
 * - объявлять человекочитаемое [title] для меню;
 * - в [run] выполнять один сценарий работы с моделью, используя [prompt], который
 *   пользователь ввёл в консоли (или дефолтный, если ввод был пустым).
 *
 * Опционально может:
 * - обрабатывать специфичные команды через [handleCommand];
 * - предоставлять справку по своим командам через [getHelpText].
 */
interface Task {
    val title: String

    /**
     * @param prompt пользовательский промпт (либо дефолтный, если ввод был пустым).
     */
    fun run(prompt: String)

    /**
     * Обрабатывает специфичную для задачи команду.
     *
     * Вызывается после того, как Menu проверил глобальные команды.
     * Если команда не распознана, должна вернуть false.
     *
     * @param input ввод пользователя (уже trim'нутый)
     * @return true если команда была обработана, false если это не команда задачи
     */
    fun handleCommand(input: String): Boolean = false

    /**
     * Возвращает текст справки по специфичным командам задачи.
     *
     * Если задача не имеет специфичных команд, возвращает null.
     * Текст будет добавлен к глобальной справке при вызове :help.
     *
     * @return текст справки или null если нет специфичных команд
     */
    fun getHelpText(): String? = null

    /**
     * Возвращает текст приглашения к вводу для задачи.
     *
     * Может содержать информацию о специфичных командах и текущих параметрах.
     * Если задача не имеет специфичного приглашения, возвращает null.
     *
     * @return текст приглашения или null для использования стандартного
     */
    fun getPromptHint(): String? = null
}

/**
 * Реестр доступных задач.
 *
 * Чтобы добавить новую задачу, достаточно:
 * 1) реализовать [Task];
 * 2) добавить её в список, возвращаемый функцией [all].
 */
object TaskRegistry {
    fun all(config: Config): List<Task> {
        val llmClient = LlmClient(config)
        return listOf(
            Task1(config, llmClient),
            Task2(config, llmClient),
            Task3(config, llmClient),
        )
    }
}
