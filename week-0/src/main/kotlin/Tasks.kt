package io.averkhogliad.ai.challenge.week0

import io.averkhogliad.ai.challenge.utils.config.Config

/**
 * Контракт учебной задачи в модуле `week-0`.
 *
 * Реализация должна:
 * - объявлять человекочитаемое [title] для меню;
 * - в [run] выполнять один сценарий работы с моделью, используя [prompt], который
 *   пользователь ввёл в консоли (или дефолтный, если ввод был пустым).
 */
interface Task {
    val title: String

    /**
     * @param prompt пользовательский промпт (либо дефолтный, если ввод был пустым).
     */
    fun run(prompt: String)
}

/**
 * Реестр доступных задач.
 *
 * Чтобы добавить новую задачу, достаточно:
 * 1) реализовать [Task];
 * 2) добавить её в список [ALL].
 */
object TaskRegistry {
    fun all(config: Config): List<Task> = listOf(
        Task1(config),
    )
}
