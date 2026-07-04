package io.averkhogliad.ai.challenge.week4.cli.cli.chat

/**
 * Парсер команд чата и памяти задачи.
 *
 * Чистая функция без побочных эффектов.
 * Распознаёт команды:
 * - :chat-new, :chat-list, :chat-switch <id>, :chat-rename <name>, :chat-delete <id>, :chat-archive
 * - :chat-history [N]
 * - :task-state, :task-reset, :task-goal <text>
 * - :task-term add <name> <definition>, :task-term remove <name>
 * - :task-constraint add <text>, :task-constraint remove <index>
 */
object ChatCommandParser {

    /**
     * Парсит строку команды чата вида `:chat-* [args]` в [ChatCommand].
     *
     * @param commandName имя команды без префикса `:` (например "chat-new")
     * @param args аргументы команды (всё после имени команды)
     * @param raw полная строка ввода (для Unknown)
     * @return [ChatCommand] или null, если команда не распознана
     */
    fun parseChatCommand(commandName: String, args: String): ChatCommand? {
        return when (commandName) {
            "chat-new" -> ChatCommand.New
            "chat-list" -> ChatCommand.List
            "chat-switch" -> {
                if (args.isEmpty()) return null
                ChatCommand.Switch(args)
            }

            "chat-rename" -> {
                if (args.isEmpty()) return null
                ChatCommand.Rename(args)
            }

            "chat-delete" -> {
                if (args.isEmpty()) return null
                ChatCommand.Delete(args)
            }

            "chat-archive" -> ChatCommand.Archive
            "chat-history" -> {
                val limit = args.toIntOrNull() ?: 10
                ChatCommand.History(limit)
            }

            else -> null
        }
    }

    /**
     * Парсит строку команды памяти вида `:task-* [args]` в [TaskStateCommand].
     *
     * @param commandName имя команды без префикса `:` (например "task-state")
     * @param args аргументы команды (всё после имени команды)
     * @return [TaskStateCommand] или null, если команда не распознана
     */
    fun parseTaskStateCommand(commandName: String, args: String): TaskStateCommand? {
        return when (commandName) {
            "task-state" -> TaskStateCommand.Show
            "task-reset" -> TaskStateCommand.Reset
            "task-goal" -> {
                if (args.isEmpty()) return null
                TaskStateCommand.SetGoal(args)
            }

            "task-term" -> parseTaskTerm(args)
            "task-constraint" -> parseTaskConstraint(args)
            else -> null
        }
    }

    private fun parseTaskTerm(args: String): TaskStateCommand? {
        val parts = args.split(" ", limit = 3)
        val subCommand = parts.getOrElse(0) { "" }.lowercase()
        val rest = parts.drop(1)

        return when (subCommand) {
            "add" -> {
                if (rest.size < 2) return null
                TaskStateCommand.AddTerm(rest[0], rest[1])
            }

            "remove" -> {
                if (rest.isEmpty()) return null
                TaskStateCommand.RemoveTerm(rest[0])
            }

            else -> null
        }
    }

    private fun parseTaskConstraint(args: String): TaskStateCommand? {
        val parts = args.split(" ", limit = 2)
        val subCommand = parts.getOrElse(0) { "" }.lowercase()
        val rest = parts.getOrElse(1) { "" }.trim()

        return when (subCommand) {
            "add" -> {
                if (rest.isEmpty()) return null
                TaskStateCommand.AddConstraint(rest)
            }

            "remove" -> {
                val index = rest.toIntOrNull() ?: return null
                TaskStateCommand.RemoveConstraint(index)
            }

            else -> null
        }
    }
}
