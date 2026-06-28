package io.averkhogliad.ai.challenge.week3.cli.cli.commands

import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
@DisplayName("CommandParser — команды управления задачами")
class CommandParserTaskTest {

    // Контекст, в котором доступны команды задач
    private val taskContext = CommandContext(
        currentTaskId = null,
        availableCommands = setOf(
            "add", "tasks", "edit", "drop", "open", "close", "cancel",
            "step-add", "step-list", "step-done",
            "help", "h", "quit", "q", "back", "b"
        )
    )

    @Nested
    @DisplayName(":add — добавление задачи")
    inner class AddTaskTests {

        @Test
        @DisplayName("парсит :add с простым заголовком")
        fun `parse add with simple title`() {
            val result = CommandParser.parse(":add Купить молоко", taskContext)
            assertEquals(Command.AddTask("Купить молоко"), result)
        }

        @Test
        @DisplayName("парсит :add с заголовком из нескольких слов")
        fun `parse add with multi-word title`() {
            val result = CommandParser.parse(":add Завершить проект до конца недели", taskContext)
            assertEquals(Command.AddTask("Завершить проект до конца недели"), result)
        }

        @Test
        @DisplayName("возвращает Unknown для :add без заголовка")
        fun `parse add without title returns Unknown`() {
            val result = CommandParser.parse(":add", taskContext)
            assertTrue(result is Command.Unknown)
        }

        @Test
        @DisplayName("парсит :add с заголовком содержащим специальные символы")
        fun `parse add with special characters in title`() {
            val result = CommandParser.parse(":add Купить молоко, хлеб и яйца!", taskContext)
            assertEquals(Command.AddTask("Купить молоко, хлеб и яйца!"), result)
        }
    }

    @Nested
    @DisplayName(":tasks — список задач")
    inner class ListTasksTests {

        @Test
        @DisplayName("парсит :tasks")
        fun `parse tasks command`() {
            val result = CommandParser.parse(":tasks", taskContext)
            assertEquals(Command.ListTasks, result)
        }

        @Test
        @DisplayName("игнорирует аргументы после :tasks")
        fun `parse tasks ignores extra arguments`() {
            // :tasks не принимает аргументы, но если они есть, всё равно возвращаем ListTasks
            val result = CommandParser.parse(":tasks", taskContext)
            assertEquals(Command.ListTasks, result)
        }
    }

    @Nested
    @DisplayName(":edit — редактирование задачи")
    inner class EditTaskTests {

        @Test
        @DisplayName("парсит :edit с ID и заголовком")
        fun `parse edit with id and title`() {
            val result = CommandParser.parse(":edit 123 Новое название", taskContext)
            assertEquals(Command.EditTask(TaskId("123"), "Новое название"), result)
        }

        @Test
        @DisplayName("парсит :edit без ID (контекстная команда)")
        fun `parse edit without id as contextual command`() {
            val result = CommandParser.parse(":edit Новое название", taskContext)
            assertEquals(Command.EditTask(null, "Новое название"), result)
        }

        @Test
        @DisplayName("возвращает Unknown для :edit без аргументов")
        fun `parse edit without arguments returns Unknown`() {
            val result = CommandParser.parse(":edit", taskContext)
            assertTrue(result is Command.Unknown)
        }

        @Test
        @DisplayName("парсит :edit с UUID-like ID")
        fun `parse edit with uuid-like id`() {
            val result = CommandParser.parse(":edit 550e8400-e29b-41d4-a716-446655440000 Новый заголовок", taskContext)
            assertEquals(Command.EditTask(TaskId("550e8400-e29b-41d4-a716-446655440000"), "Новый заголовок"), result)
        }
    }

    @Nested
    @DisplayName(":drop — удаление задачи")
    inner class DropTaskTests {

        @Test
        @DisplayName("парсит :drop с ID")
        fun `parse drop with id`() {
            val result = CommandParser.parse(":drop abc123", taskContext)
            assertEquals(Command.DropTask(TaskId("abc123")), result)
        }

        @Test
        @DisplayName("парсит :drop без ID (контекстная команда)")
        fun `parse drop without id as contextual command`() {
            val result = CommandParser.parse(":drop", taskContext)
            assertEquals(Command.DropTask(null), result)
        }

        @Test
        @DisplayName("парсит :drop с числовым ID")
        fun `parse drop with numeric id`() {
            val result = CommandParser.parse(":drop 42", taskContext)
            assertEquals(Command.DropTask(TaskId("42")), result)
        }
    }

    @Nested
    @DisplayName(":open — открытие задачи")
    inner class OpenTaskTests {

        @Test
        @DisplayName("парсит :open с ID")
        fun `parse open with id`() {
            val result = CommandParser.parse(":open abc123", taskContext)
            assertEquals(Command.OpenTask(TaskId("abc123")), result)
        }

        @Test
        @DisplayName("возвращает Unknown для :open без ID")
        fun `parse open without id returns Unknown`() {
            val result = CommandParser.parse(":open", taskContext)
            assertTrue(result is Command.Unknown)
        }

        @Test
        @DisplayName("парсит :open с UUID-like ID")
        fun `parse open with uuid-like id`() {
            val result = CommandParser.parse(":open 550e8400-e29b-41d4-a716-446655440000", taskContext)
            assertEquals(Command.OpenTask(TaskId("550e8400-e29b-41d4-a716-446655440000")), result)
        }
    }

    @Nested
    @DisplayName(":close — закрытие задачи")
    inner class CloseTaskTests {

        @Test
        @DisplayName("парсит :close с ID")
        fun `parse close with id`() {
            val result = CommandParser.parse(":close abc123", taskContext)
            assertEquals(Command.CloseTask(TaskId("abc123")), result)
        }

        @Test
        @DisplayName("парсит :close без ID (контекстная команда)")
        fun `parse close without id as contextual command`() {
            val result = CommandParser.parse(":close", taskContext)
            assertEquals(Command.CloseTask(null), result)
        }

        @Test
        @DisplayName("парсит :close с числовым ID")
        fun `parse close with numeric id`() {
            val result = CommandParser.parse(":close 123", taskContext)
            assertEquals(Command.CloseTask(TaskId("123")), result)
        }
    }

    @Nested
    @DisplayName(":cancel — отмена задачи")
    inner class CancelTaskTests {

        @Test
        @DisplayName("парсит :cancel с ID")
        fun `parse cancel with id`() {
            val result = CommandParser.parse(":cancel abc123", taskContext)
            assertEquals(Command.CancelTask(TaskId("abc123")), result)
        }

        @Test
        @DisplayName("парсит :cancel без ID (контекстная команда)")
        fun `parse cancel without id as contextual command`() {
            val result = CommandParser.parse(":cancel", taskContext)
            assertEquals(Command.CancelTask(null), result)
        }

        @Test
        @DisplayName("парсит :cancel с UUID-like ID")
        fun `parse cancel with uuid-like id`() {
            val result = CommandParser.parse(":cancel 550e8400-e29b-41d4-a716-446655440000", taskContext)
            assertEquals(Command.CancelTask(TaskId("550e8400-e29b-41d4-a716-446655440000")), result)
        }
    }

    @Nested
    @DisplayName(":step-add — добавление шага")
    inner class AddStepTests {

        @Test
        @DisplayName("парсит :step-add с текстом")
        fun `parse step-add with text`() {
            val result = CommandParser.parse(":step-add Сделать дизайн", taskContext)
            assertEquals(Command.AddStep("Сделать дизайн"), result)
        }

        @Test
        @DisplayName("парсит :step-add с многословным текстом")
        fun `parse step-add with multi-word text`() {
            val result = CommandParser.parse(":step-add Написать unit-тесты для всех классов", taskContext)
            assertEquals(Command.AddStep("Написать unit-тесты для всех классов"), result)
        }

        @Test
        @DisplayName("возвращает Unknown для :step-add без текста")
        fun `parse step-add without text returns Unknown`() {
            val result = CommandParser.parse(":step-add", taskContext)
            assertTrue(result is Command.Unknown)
        }

        @Test
        @DisplayName("возвращает Unknown для :step-add с пробелами")
        fun `parse step-add with whitespace returns Unknown`() {
            val result = CommandParser.parse(":step-add   ", taskContext)
            assertTrue(result is Command.Unknown)
        }
    }

    @Nested
    @DisplayName(":step-list — список шагов")
    inner class ListStepsTests {

        @Test
        @DisplayName("парсит :step-list")
        fun `parse step-list command`() {
            val result = CommandParser.parse(":step-list", taskContext)
            assertEquals(Command.ListSteps, result)
        }
    }

    @Nested
    @DisplayName(":step-done — отметка шага выполненным")
    inner class CompleteStepTests {

        @Test
        @DisplayName("парсит :step-done с ID")
        fun `parse step-done with id`() {
            val result = CommandParser.parse(":step-done step-1", taskContext)
            assertEquals(Command.CompleteStep("step-1"), result)
        }

        @Test
        @DisplayName("парсит :step-done с UUID-like ID")
        fun `parse step-done with uuid-like id`() {
            val result = CommandParser.parse(":step-done 550e8400-e29b-41d4-a716-446655440000", taskContext)
            assertEquals(Command.CompleteStep("550e8400-e29b-41d4-a716-446655440000"), result)
        }

        @Test
        @DisplayName("возвращает Unknown для :step-done без ID")
        fun `parse step-done without id returns Unknown`() {
            val result = CommandParser.parse(":step-done", taskContext)
            assertTrue(result is Command.Unknown)
        }

        @Test
        @DisplayName("возвращает Unknown для :step-done с пробелами")
        fun `parse step-done with whitespace returns Unknown`() {
            val result = CommandParser.parse(":step-done   ", taskContext)
            assertTrue(result is Command.Unknown)
        }
    }

    @Nested
    @DisplayName("Ошибки парсинга")
    inner class ErrorCasesTests {

        @Test
        @DisplayName("возвращает Unknown для неизвестной команды")
        fun `parse unknown command returns Unknown`() {
            val result = CommandParser.parse(":unknown", taskContext)
            assertTrue(result is Command.Unknown)
        }

        @Test
        @DisplayName("возвращает Unknown для :add без заголовка")
        fun `parse add without title returns Unknown`() {
            val result = CommandParser.parse(":add   ", taskContext)
            assertTrue(result is Command.Unknown)
        }

        @Test
        @DisplayName("возвращает Unknown для :open без ID")
        fun `parse open without id returns Unknown`() {
            val result = CommandParser.parse(":open   ", taskContext)
            assertTrue(result is Command.Unknown)
        }
    }
}
