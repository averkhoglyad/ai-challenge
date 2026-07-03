package io.averkhogliad.ai.challenge.week4.cli.unit.cli.commands

import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.CommandParser
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Тесты для парсинга команд управления задачами todo-менеджера.
 *
 * Покрывает парсинг команд:
 * - :add <title>
 * - :tasks
 * - :edit <id> <title> / :edit <title>
 * - :drop <id> / :drop
 * - :open <id>
 * - :close <id> / :close
 * - :cancel <id> / :cancel
 */
class CommandParserTaskTest : FreeSpec({

    // Контекст, в котором доступны команды задач
    val taskContext = CommandContext(
        currentTaskId = null,
        availableCommands = setOf(
            "add", "tasks", "edit", "drop", "open", "close", "cancel",
            "step-add", "step-list", "step-done",
            "help", "h", "quit", "q", "back", "b"
        )
    )

    ":add — добавление задачи" - {
        "парсит :add с простым заголовком" {
            // given
            // when
            val result = CommandParser.parse(":add Купить молоко", taskContext)
            // then
            result shouldBe Command.AddTask("Купить молоко")
        }

        "парсит :add с заголовком из нескольких слов" {
            // given
            // when
            val result = CommandParser.parse(":add Завершить проект до конца недели", taskContext)
            // then
            result shouldBe Command.AddTask("Завершить проект до конца недели")
        }

        "возвращает Unknown для :add без заголовка" {
            // given
            // when
            val result = CommandParser.parse(":add", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "парсит :add с заголовком содержащим специальные символы" {
            // given
            // when
            val result = CommandParser.parse(":add Купить молоко, хлеб и яйца!", taskContext)
            // then
            result shouldBe Command.AddTask("Купить молоко, хлеб и яйца!")
        }
    }

    ":tasks — список задач" - {
        "парсит :tasks" {
            // given
            // when
            val result = CommandParser.parse(":tasks", taskContext)
            // then
            result shouldBe Command.ListTasks
        }

        "игнорирует аргументы после :tasks" {
            // given
            // :tasks не принимает аргументы, но если они есть, всё равно возвращаем ListTasks
            // when
            val result = CommandParser.parse(":tasks", taskContext)
            // then
            result shouldBe Command.ListTasks
        }
    }

    ":edit — редактирование задачи" - {
        "парсит :edit с ID и заголовком" {
            // given
            // when
            val result = CommandParser.parse(":edit 123 Новое название", taskContext)
            // then
            result shouldBe Command.EditTask(TaskId("123"), "Новое название")
        }

        "парсит :edit без ID (контекстная команда)" {
            // given
            // when
            val result = CommandParser.parse(":edit Новое название", taskContext)
            // then
            result shouldBe Command.EditTask(null, "Новое название")
        }

        "возвращает Unknown для :edit без аргументов" {
            // given
            // when
            val result = CommandParser.parse(":edit", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "парсит :edit с UUID-like ID" {
            // given
            // when
            val result = CommandParser.parse(":edit 550e8400-e29b-41d4-a716-446655440000 Новый заголовок", taskContext)
            // then
            result shouldBe Command.EditTask(TaskId("550e8400-e29b-41d4-a716-446655440000"), "Новый заголовок")
        }
    }

    ":drop — удаление задачи" - {
        "парсит :drop с ID" {
            // given
            // when
            val result = CommandParser.parse(":drop abc123", taskContext)
            // then
            result shouldBe Command.DropTask(TaskId("abc123"))
        }

        "парсит :drop без ID (контекстная команда)" {
            // given
            // when
            val result = CommandParser.parse(":drop", taskContext)
            // then
            result shouldBe Command.DropTask(null)
        }

        "парсит :drop с числовым ID" {
            // given
            // when
            val result = CommandParser.parse(":drop 42", taskContext)
            // then
            result shouldBe Command.DropTask(TaskId("42"))
        }
    }

    ":open — открытие задачи" - {
        "парсит :open с ID" {
            // given
            // when
            val result = CommandParser.parse(":open abc123", taskContext)
            // then
            result shouldBe Command.OpenTask(TaskId("abc123"))
        }

        "возвращает Unknown для :open без ID" {
            // given
            // when
            val result = CommandParser.parse(":open", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "парсит :open с UUID-like ID" {
            // given
            // when
            val result = CommandParser.parse(":open 550e8400-e29b-41d4-a716-446655440000", taskContext)
            // then
            result shouldBe Command.OpenTask(TaskId("550e8400-e29b-41d4-a716-446655440000"))
        }
    }

    ":close — закрытие задачи" - {
        "парсит :close с ID" {
            // given
            // when
            val result = CommandParser.parse(":close abc123", taskContext)
            // then
            result shouldBe Command.CloseTask(TaskId("abc123"))
        }

        "парсит :close без ID (контекстная команда)" {
            // given
            // when
            val result = CommandParser.parse(":close", taskContext)
            // then
            result shouldBe Command.CloseTask(null)
        }

        "парсит :close с числовым ID" {
            // given
            // when
            val result = CommandParser.parse(":close 123", taskContext)
            // then
            result shouldBe Command.CloseTask(TaskId("123"))
        }
    }

    ":cancel — отмена задачи" - {
        "парсит :cancel с ID" {
            // given
            // when
            val result = CommandParser.parse(":cancel abc123", taskContext)
            // then
            result shouldBe Command.CancelTask(TaskId("abc123"))
        }

        "парсит :cancel без ID (контекстная команда)" {
            // given
            // when
            val result = CommandParser.parse(":cancel", taskContext)
            // then
            result shouldBe Command.CancelTask(null)
        }

        "парсит :cancel с UUID-like ID" {
            // given
            // when
            val result = CommandParser.parse(":cancel 550e8400-e29b-41d4-a716-446655440000", taskContext)
            // then
            result shouldBe Command.CancelTask(TaskId("550e8400-e29b-41d4-a716-446655440000"))
        }
    }

    ":step-add — добавление шага" - {
        "парсит :step-add с текстом" {
            // given
            // when
            val result = CommandParser.parse(":step-add Сделать дизайн", taskContext)
            // then
            result shouldBe Command.AddStep("Сделать дизайн")
        }

        "парсит :step-add с многословным текстом" {
            // given
            // when
            val result = CommandParser.parse(":step-add Написать unit-тесты для всех классов", taskContext)
            // then
            result shouldBe Command.AddStep("Написать unit-тесты для всех классов")
        }

        "возвращает Unknown для :step-add без текста" {
            // given
            // when
            val result = CommandParser.parse(":step-add", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "возвращает Unknown для :step-add с пробелами" {
            // given
            // when
            val result = CommandParser.parse(":step-add   ", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }
    }

    ":step-list — список шагов" - {
        "парсит :step-list" {
            // given
            // when
            val result = CommandParser.parse(":step-list", taskContext)
            // then
            result shouldBe Command.ListSteps
        }
    }

    ":step-done — отметка шага выполненным" - {
        "парсит :step-done с ID" {
            // given
            // when
            val result = CommandParser.parse(":step-done step-1", taskContext)
            // then
            result shouldBe Command.CompleteStep("step-1")
        }

        "парсит :step-done с UUID-like ID" {
            // given
            // when
            val result = CommandParser.parse(":step-done 550e8400-e29b-41d4-a716-446655440000", taskContext)
            // then
            result shouldBe Command.CompleteStep("550e8400-e29b-41d4-a716-446655440000")
        }

        "возвращает Unknown для :step-done без ID" {
            // given
            // when
            val result = CommandParser.parse(":step-done", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "возвращает Unknown для :step-done с пробелами" {
            // given
            // when
            val result = CommandParser.parse(":step-done   ", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }
    }

    "Ошибки парсинга" - {
        "возвращает Unknown для неизвестной команды" {
            // given
            // when
            val result = CommandParser.parse(":unknown", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "возвращает Unknown для :add без заголовка" {
            // given
            // when
            val result = CommandParser.parse(":add   ", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }

        "возвращает Unknown для :open без ID" {
            // given
            // when
            val result = CommandParser.parse(":open   ", taskContext)
            // then
            result.shouldBeInstanceOf<Command.Unknown>()
        }
    }
})
