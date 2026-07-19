package io.averkhogliad.ai.challenge.week6.cli.handlers.release

import io.averkhogliad.ai.challenge.week6.application.release.*
import io.averkhogliad.ai.challenge.week6.cli.rendering.ReleaseDetailRenderer
import io.averkhogliad.ai.challenge.week6.cli.rendering.ReleaseTableRenderer
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.Project
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import kotlinx.coroutines.flow.toList
import java.nio.file.Path

class ReleaseCommandHandler(
    private val generateReleaseNotesUseCase: GenerateReleaseNotesUseCase,
    private val confirmReleaseUseCaseFactory: (Project) -> ConfirmReleaseUseCase,
    private val activeProject: () -> Project?,
) : CommandHandler {

    override val name: String = "/release"
    override val description: String = "Generate release notes: /release [version] [--range base..head]"

    override fun canHandle(rawInput: String): Boolean =
        (rawInput == name || rawInput.startsWith("$name ")) &&
                !rawInput.startsWith("/release suggest") &&
                !rawInput.startsWith("/release history") &&
                !rawInput.startsWith("/release show")

    override suspend fun execute(rawInput: String): CommandEffect {
        val project = activeProject() ?: return CommandEffect.DisplayDomainError(DomainError.noActiveProject())
        val arguments = rawInput.removePrefix(name).trim()
        val range = RANGE_REGEX.find(arguments)?.groupValues?.get(1)
        val version = arguments.replace(RANGE_REGEX, "").trim().ifBlank { null }
        if (version != null && version.split(Regex("\\s+")).size > 1) {
            return CommandEffect.Print("Usage: /release [version] [--range base..head]", isError = true)
        }

        val progress = generateReleaseNotesUseCase.execute(
            ReleaseRequest(project.id, project.rootPath, version, range),
        ).toList()
        val error = progress.filterIsInstance<ReleaseProgress.Error>().firstOrNull()
        if (error != null) return CommandEffect.DisplayDomainError(error.error)
        val draft = progress.filterIsInstance<ReleaseProgress.PreviewReady>().firstOrNull()?.draft
            ?: return CommandEffect.Print("Release preview was not generated.", isError = true)
        val warnings =
            progress.filterIsInstance<ReleaseProgress.Warning>().joinToString("\n") { "Warning: ${it.message}" }
        val message = buildString {
            if (warnings.isNotBlank()) appendLine(warnings)
            appendLine("CHANGELOG preview:")
            appendLine(draft.markdown)
            append("Apply changes to CHANGELOG.md?")
        }
        // TODO: common:repl lacks an effect that composes streaming progress with a following confirmation.
        return CommandEffect.Confirm(
            message = message,
            onConfirm = {
                val currentProject =
                    activeProject() ?: return@Confirm CommandEffect.DisplayDomainError(DomainError.noActiveProject())
                if (currentProject.id != draft.release.projectId) {
                    return@Confirm CommandEffect.Print(
                        "Active project changed. Generate release preview again.",
                        isError = true
                    )
                }
                when (val persisted = confirmReleaseUseCaseFactory(currentProject).execute(draft)) {
                    is DomainResult.Success -> CommandEffect.Print("CHANGELOG.md updated. Release ${draft.release.version} saved to database.")
                    is DomainResult.Failure -> CommandEffect.DisplayDomainError(persisted.error)
                }
            },
        )
    }

    private companion object {
        val RANGE_REGEX = Regex("(?:^|\\s)--range\\s+(\\S+)")
    }
}

class ReleaseSuggestHandler(
    private val generateReleaseNotesUseCase: GenerateReleaseNotesUseCase,
    private val rootPath: () -> Path?,
    private val projectId: () -> String?,
) : CommandHandler {

    override val name: String = "/release suggest"
    override val description: String = "Suggest a release version: /release suggest [--range base..head]"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == name || rawInput.startsWith("$name ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val projectId = projectId() ?: return CommandEffect.DisplayDomainError(DomainError.noActiveProject())
        val rootPath = rootPath() ?: return CommandEffect.DisplayDomainError(DomainError.noActiveProject())
        val arguments = rawInput.removePrefix(name).trim()
        val range = Regex("--range\\s+(\\S+)").find(arguments)?.groupValues?.get(1)
        if (arguments.isNotBlank() && range == null) {
            return CommandEffect.Print("Usage: /release suggest [--range base..head]", isError = true)
        }
        val progress = generateReleaseNotesUseCase.execute(
            ReleaseRequest(projectId, rootPath, version = null, range = range),
        ).toList()
        val error = progress.filterIsInstance<ReleaseProgress.Error>().firstOrNull()
        if (error != null) return CommandEffect.DisplayDomainError(error.error)
        val draft = progress.filterIsInstance<ReleaseProgress.PreviewReady>().firstOrNull()?.draft
            ?: return CommandEffect.Print("Version suggestion was not generated.", isError = true)
        return CommandEffect.Print(
            "Suggested: ${draft.release.version}\n" +
                    "Reasoning: ${draft.release.changelog.summary}\n" +
                    "Run /release ${draft.release.version} to preview and apply the changelog.",
        )
    }
}

class ReleaseHistoryHandler(
    private val listReleasesUseCase: ListReleasesUseCase,
    private val releaseTableRenderer: ReleaseTableRenderer,
    private val projectId: () -> String?,
) : CommandHandler {

    override val name: String = "/release history"
    override val description: String = "Show release history: /release history [--limit N]"

    override fun canHandle(rawInput: String): Boolean = rawInput == name || rawInput.startsWith("$name ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val projectId = projectId() ?: return CommandEffect.DisplayDomainError(DomainError.noActiveProject())
        val arguments = rawInput.removePrefix(name).trim()
        val limit =
            if (arguments.isBlank()) 10 else LIMIT_REGEX.matchEntire(arguments)?.groupValues?.get(1)?.toIntOrNull()
                ?: return CommandEffect.Print("Usage: /release history [--limit N]", isError = true)
        return when (val result = listReleasesUseCase.execute(projectId, limit)) {
            is DomainResult.Success -> CommandEffect.Print(releaseTableRenderer.render(result.value))
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }

    private companion object {
        val LIMIT_REGEX = Regex("--limit\\s+(\\d+)")
    }
}

class ReleaseShowHandler(
    private val showReleaseUseCase: ShowReleaseUseCase,
    private val releaseDetailRenderer: ReleaseDetailRenderer,
    private val projectId: () -> String?,
) : CommandHandler {

    override val name: String = "/release show"
    override val description: String = "Show release changelog: /release show <version>"

    override fun canHandle(rawInput: String): Boolean = rawInput.startsWith("$name ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val projectId = projectId() ?: return CommandEffect.DisplayDomainError(DomainError.noActiveProject())
        val version = rawInput.removePrefix(name).trim()
        if (version.isBlank() || version.contains(Regex("\\s"))) {
            return CommandEffect.Print("Usage: /release show <version>", isError = true)
        }
        return when (val result = showReleaseUseCase.execute(projectId, version)) {
            is DomainResult.Success -> CommandEffect.Print(releaseDetailRenderer.render(result.value))
            is DomainResult.Failure -> CommandEffect.DisplayDomainError(result.error)
        }
    }
}
