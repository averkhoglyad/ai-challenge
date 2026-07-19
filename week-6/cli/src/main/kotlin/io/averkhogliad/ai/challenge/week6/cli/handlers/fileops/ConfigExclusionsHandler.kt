package io.averkhogliad.ai.challenge.week6.cli.handlers.fileops

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.fileops.ExclusionList
import io.averkhogliad.ai.challenge.week6.application.fileops.ProjectSettingsRepository
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler

class ConfigExclusionsHandler(
    private val settingsRepo: ProjectSettingsRepository,
    private val projectContextProvider: ProjectContextProvider,
) : CommandHandler {

    override val name: String = "/config exclusions"

    override val description: String = buildString {
        appendLine("Управление списком исключений файловых операций.")
        appendLine("  /config exclusions show      — показать текущий список")
        appendLine("  /config exclusions add <p>   — добавить паттерн")
        appendLine("  /config exclusions remove <p>— удалить паттерн")
        appendLine("  /config exclusions reset     — сбросить к заводским настройкам")
    }

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/config exclusions" || rawInput.startsWith("/config exclusions ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val ctx = when (val r = projectContextProvider.getContext()) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> return CommandEffect.DisplayDomainError(r.error)
        } ?: return CommandEffect.Print("No active project. Use /open first.", isError = true)

        val args = rawInput.removePrefix("/config exclusions").trim()
        val projectId = ctx.projectId
        val custom = settingsRepo.loadExclusions(projectId)
        val defaults = ExclusionList.DEFAULT_EXCLUSIONS

        return when {
            args.isEmpty() || args == "show" -> showCommand(custom, defaults)
            args.startsWith("add ") -> addCommand(args.removePrefix("add ").trim(), custom, defaults, projectId)
            args.startsWith("remove ") -> removeCommand(
                args.removePrefix("remove ").trim(),
                custom,
                defaults,
                projectId
            )

            args == "reset" -> resetCommand(projectId)
            else -> CommandEffect.Print("Unknown subcommand: $args\n\n$description", isError = true)
        }
    }

    private fun showCommand(custom: List<String>, defaults: List<String>): CommandEffect {
        val active = defaults + custom
        val deletedFromDefaults = defaults.filter { it !in active }
        val addedByUser = active.filter { it !in defaults }

        val text = buildString {
            appendLine("**Текущие исключения:**")
            active.forEachIndexed { i, p -> appendLine("  ${i + 1}. $p") }
            appendLine()
            if (deletedFromDefaults.isNotEmpty()) {
                appendLine("**Удалены из заводских:**")
                deletedFromDefaults.forEach { appendLine("  - $it") }
                appendLine()
            }
            if (addedByUser.isNotEmpty()) {
                appendLine("**Добавлены пользователем:**")
                addedByUser.forEach { appendLine("  + $it") }
            }
        }
        return CommandEffect.Print(text)
    }

    private fun addCommand(
        pattern: String,
        custom: List<String>,
        defaults: List<String>,
        projectId: String
    ): CommandEffect {
        if (pattern.isBlank()) {
            return CommandEffect.Print("Укажите паттерн: /config exclusions add <pattern>", isError = true)
        }

        val effective = defaults + custom
        if (pattern in effective) {
            return CommandEffect.Print("Паттерн '$pattern' уже в списке исключений.", isError = true)
        }

        val newList = custom + pattern
        return when (val r = settingsRepo.saveExclusions(projectId, newList)) {
            is DomainResult.Success -> CommandEffect.Print("Паттерн '$pattern' добавлен в исключения (перезапустите /open для применения).")
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(r.error)
        }
    }

    private fun removeCommand(
        pattern: String,
        custom: List<String>,
        defaults: List<String>,
        projectId: String
    ): CommandEffect {
        if (pattern.isBlank()) {
            return CommandEffect.Print("Укажите паттерн: /config exclusions remove <pattern>", isError = true)
        }

        val effective = defaults + custom
        if (pattern !in effective) {
            return CommandEffect.Print("Паттерн '$pattern' не найден в списке исключений.", isError = true)
        }

        val newList = custom - pattern
        return when (val r = settingsRepo.saveExclusions(projectId, newList)) {
            is DomainResult.Success -> CommandEffect.Print("Паттерн '$pattern' удалён из исключений (перезапустите /open для применения).")
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(r.error)
        }
    }

    private fun resetCommand(projectId: String): CommandEffect {
        return when (val r = settingsRepo.saveExclusions(projectId, emptyList())) {
            is DomainResult.Success -> CommandEffect.Print("Исключения сброшены к заводским настройкам (перезапустите /open для применения).")
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(r.error)
        }
    }
}
