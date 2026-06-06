package io.averkhogliad.ai.challenge.week0

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Panel
import io.averkhogliad.ai.challenge.utils.config.Config

/**
 * Интерактивное меню выбора задачи и ввода промпта.
 *
 * Использует Mordant для красивого консольного UI:
 * - Выбор задачи вводом номера
 * - Цветной вывод с подсветкой
 * - Панели и разделители
 *
 * После выбора задачи крутит REPL-цикл: пользователь может слать модели
 * новые промпты подряд, пока не выйдет командой `:q` (или `:quit`)
 * либо не прервёт работу `Ctrl-C`.
 *
 * Глобальные команды (работают на любом этапе — и в меню выбора задачи, и в REPL):
 *  - `:quit` (или `:q`) — выйти из приложения;
 *  - `:help` (или `:h`) — напечатать подсказку.
 *
 * Команды REPL (доступны только после выбора задачи):
 *  - `:t` (или `:task`) — выбрать другую задачу (игнорируется на этапе выбора задачи).
 */
object Menu {

    /** Дефолтный промпт, если пользователь ничего не ввёл. */
    private const val DEFAULT_PROMPT = "Расскажи короткий анекдот про программиста."

    /** Mordant Terminal для красивого вывода. */
    private val mordantTerminal = Terminal()

    /** Конфигурация приложения, устанавливается при вызове [mainLoop]. */
    private lateinit var config: Config

    /**
     * Показывает меню с вводом номера задачи.
     */
    private fun numericSelect(tasks: List<Task>): Task? {
        while (true) {
            mordantTerminal.println()
            
            val menuContent = buildString {
                appendLine()
                tasks.forEachIndexed { index, task ->
                    appendLine("  ${bold(yellow("${index + 1}"))}  ${task.title}")
                }
                appendLine()
                appendLine("  ${bold(red("0"))}  Выход")
                appendLine()
            }
            
            mordantTerminal.println(Panel(menuContent, title = bold(cyan("📋 Доступные задачи"))))
            
            mordantTerminal.print(bold(yellow("❯ ")))
            mordantTerminal.print(gray("Введите номер задачи: "))
            val input = readlnOrNull()?.trim().orEmpty()
            
            when {
                input == ":quit" || input == ":q" || input == "0" -> {
                    return null
                }
                input == ":help" || input == ":h" -> {
                    printHelp()
                    continue
                }
                input == ":task" || input == ":t" -> {
                    mordantTerminal.println(red("Команда :task недоступна на этапе выбора задачи."))
                    continue
                }
            }
            
            val number = input.toIntOrNull()
            if (number != null && number in 1..tasks.size) {
                return tasks[number - 1]
            }
            
            mordantTerminal.println(bold(red("✗ Некорректный ввод: ")) + red("«$input». Попробуйте ещё раз."))
        }
    }

    /**
     * Печатает список задач и читает выбор пользователя.
     * Использует ввод номера задачи.
     *
     * @return выбранная задача, либо `null`, если пользователь хочет выйти.
     */
    fun selectTask(): Task? {
        val tasks = TaskRegistry.all(config)
        require(tasks.isNotEmpty()) { "TaskRegistry.all(config) пуст — не зарегистрировано ни одной задачи." }

        return numericSelect(tasks)
    }

    /**
     * Читает одну «строку» в REPL. Возвращает:
     *  - `Exit` — пользователь хочет выйти (`:quit` / `:q`);
     *  - `SwitchTask` — пользователь хочет сменить задачу (`:t` / `:task`);
     *  - `Help` — напечатать подсказку (`:help` / `:h`);
     *  - `Prompt(text)` — обычный промпт; пустой ввод → [DEFAULT_PROMPT].
     */
    private fun readCommand(): ReplCommand {
        mordantTerminal.print(bold(yellow("❯ ")))
        mordantTerminal.print(gray("Введите промпт (Enter — дефолтный, :q — выход, :t — сменить задачу): "))
        val input = readlnOrNull()?.trim().orEmpty()
        return when {
            input.isEmpty() -> ReplCommand.Prompt(DEFAULT_PROMPT)
            input == ":quit" || input == ":q" -> ReplCommand.Exit
            input == ":task" || input == ":t" -> ReplCommand.SwitchTask
            input == ":help" || input == ":h" -> ReplCommand.Help
            else -> ReplCommand.Prompt(input)
        }
    }

    private fun printHelp() {
        mordantTerminal.println()
        
        val helpContent = buildString {
            appendLine()
            appendLine("  ${bold("<текст>")}        — отправить промпт текущей задаче")
            appendLine("  ${bold("(пустой Enter)")} — отправить дефолтный промпт")
            appendLine("  ${bold(":help / :h")}     — эта подсказка")
            appendLine("  ${bold(":task / :t")}     — выбрать другую задачу")
            appendLine("  ${bold(":quit / :q")}     — выйти из приложения")
            appendLine()
        }
        
        mordantTerminal.println(Panel(helpContent, title = bold(cyan("📖 Справка по командам"))))
        mordantTerminal.println()
    }

    /**
     * Точка входа интерактивного режима.
     *
     * Сначала выбираем задачу (пользователь может сразу выйти через 0 или :q),
     * затем крутим REPL-цикл: пользователь шлёт промпты подряд, пока не выйдет.
     * Из REPL-цикла можно сменить задачу командой `:t` (`:task`) — и тоже
     * выйти из программы, если на этом этапе выберет `:q`.
     */
    fun mainLoop(config: Config) {
        this.config = config
        mordantTerminal.println()
        mordantTerminal.println(bold(green("🤖 Добро пожаловать в AI Challenge!")))
        mordantTerminal.println(gray("Интерактивный режим работы с языковыми моделями"))
        mordantTerminal.println()

        val firstTask: Task? = selectTask()
        if (firstTask == null) {
            mordantTerminal.println()
            mordantTerminal.println(bold(yellow("👋 До свидания!")))
            return
        }
        var task: Task = firstTask
        mordantTerminal.println()
        mordantTerminal.println(bold(green("✓ Активная задача: ")) + bold(white(task.title)))
        mordantTerminal.println(gray("(введите :help для списка команд)"))
        mordantTerminal.println()

        while (true) {
            val command = readCommand()
            when (command) {
                is ReplCommand.Exit -> {
                    mordantTerminal.println()
                    mordantTerminal.println(bold(yellow("👋 До свидания!")))
                    return
                }
                is ReplCommand.SwitchTask -> {
                    val next: Task? = selectTask()
                    if (next == null) {
                        mordantTerminal.println()
                        mordantTerminal.println(bold(yellow("👋 До свидания!")))
                        return
                    }
                    task = next
                    mordantTerminal.println()
                    mordantTerminal.println(bold(green("✓ Активная задача: ")) + bold(white(task.title)))
                    mordantTerminal.println()
                }
                is ReplCommand.Help -> printHelp()
                is ReplCommand.Prompt -> {
                    mordantTerminal.println()
                    mordantTerminal.println(bold(blue("📤 Промпт: ")) + white(command.text))
                    mordantTerminal.println()
                    task.run(command.text)
                    mordantTerminal.println()
                }
            }
        }
    }

    /** Внутреннее представление одной пользовательской команды в REPL. */
    private sealed interface ReplCommand {
        data class Prompt(val text: String) : ReplCommand
        data object Exit : ReplCommand
        data object SwitchTask : ReplCommand
        data object Help : ReplCommand
    }
}
