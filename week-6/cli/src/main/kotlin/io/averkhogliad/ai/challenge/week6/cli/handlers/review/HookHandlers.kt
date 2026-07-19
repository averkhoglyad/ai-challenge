package io.averkhogliad.ai.challenge.week6.cli.handlers.review

import io.averkhogliad.ai.challenge.week6.GitHookInstaller
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import java.nio.file.Path

class ReviewInstallHookHandler(
    private val rootPath: () -> Path?,
    private val binPath: () -> Path,
) : CommandHandler {

    override val name: String = "/review install-hook"
    override val aliases: List<String> = listOf("/review install-hook")
    override val description: String = "Install post-commit git hook for automatic code review"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/review install-hook"

    override suspend fun execute(rawInput: String): CommandEffect {
        val rp = rootPath() ?: return CommandEffect.Print("No active project.", isError = true)

        val result = GitHookInstaller.install(rp, binPath())
        return when (result) {
            is GitHookInstaller.Result.Success -> CommandEffect.Print(result.message)
            is GitHookInstaller.Result.Failure -> CommandEffect.Print(result.message, isError = true)
        }
    }
}

class ReviewRemoveHookHandler(
    private val rootPath: () -> Path?,
) : CommandHandler {

    override val name: String = "/review remove-hook"
    override val aliases: List<String> = listOf("/review remove-hook")
    override val description: String = "Remove post-commit git hook"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/review remove-hook"

    override suspend fun execute(rawInput: String): CommandEffect {
        val rp = rootPath() ?: return CommandEffect.Print("No active project.", isError = true)

        val result = GitHookInstaller.remove(rp)
        return when (result) {
            is GitHookInstaller.Result.Success -> CommandEffect.Print(result.message)
            is GitHookInstaller.Result.Failure -> CommandEffect.Print(result.message, isError = true)
        }
    }
}
