package io.averkhogliad.ai.challenge.week6.application.fileops

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.cli.rendering.DiffRenderer
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.DiffLine
import io.averkhogliad.cli.repl.core.CommandEffect
import kotlinx.coroutines.flow.toList

class RefactorUseCase(
    private val orchestrator: RefactorAgentOrchestrator,
    private val diffRenderer: DiffRenderer,
    private val projectContextProvider: ProjectContextProvider,
) {
    suspend fun execute(goal: String): CommandEffect {
        // Start orchestration
        val progressEvents = mutableListOf<RefactorProgress>()

        try {
            orchestrator.orchestrate(goal).collect { event ->
                progressEvents.add(event)
            }
        } catch (e: Exception) {
            return CommandEffect.Print("Refactor error: ${e.message}", isError = true)
        }

        val awaitConfirm = progressEvents.find { it is RefactorProgress.AwaitingConfirm }
                as? RefactorProgress.AwaitingConfirm
            ?: run {
                val error = progressEvents.filterIsInstance<RefactorProgress.Error>().firstOrNull()
                return if (error != null) {
                    CommandEffect.Print("Plan error: ${error.message}", isError = true)
                } else {
                    CommandEffect.Print("Refactoring could not produce a plan.", isError = true)
                }
            }

        val diffs = awaitConfirm.diffs
        val changes = awaitConfirm.changes
        val preexistingPaths = awaitConfirm.preexistingPaths

        // Render diffs
        val diffText = diffRenderer.renderDiffs(diffs)
        val stats = buildString {
            val additions = diffs.sumOf { diff ->
                diff.hunks.sumOf { hunk ->
                    hunk.lines.count {
                        it is DiffLine.Added || it is DiffLine.Modified
                    }
                }
            }
            val deletions = diffs.sumOf { diff ->
                diff.hunks.sumOf { hunk ->
                    hunk.lines.count {
                        it is DiffLine.Removed || it is DiffLine.Modified
                    }
                }
            }
            appendLine("Files to change: ${diffs.size}")
            appendLine("Additions: +$additions")
            appendLine("Deletions: -$deletions")
        }

        val message = buildString {
            appendLine(stats)
            appendLine()
            appendLine(diffText)
            appendLine()
            append("Apply these changes? (y/n)")
        }

        return CommandEffect.Confirm(
            message = message,
            onConfirm = {
                try {
                    val execEvents = orchestrator.executeChanges(changes, preexistingPaths).toList()
                    val completed = execEvents.find { it is RefactorProgress.Completed }
                            as? RefactorProgress.Completed

                    if (completed != null) {
                        val ctx = when (val r = projectContextProvider.getContext()) {
                            is DomainResult.Success -> r.value
                            is DomainResult.Failure -> null
                        }

                        val undoHint = if (ctx?.isGitEnabled == true) {
                            val files = completed.updatedFiles.joinToString(" ") { it.toString() }
                            "To undo: git checkout -- $files"
                        } else {
                            "⚠ This project has no version control. Changes are irreversible."
                        }

                        CommandEffect.Print(
                            "✓ ${completed.updatedFiles.size} files updated.\n$undoHint"
                        )
                    } else {
                        val error = execEvents.filterIsInstance<RefactorProgress.Error>().firstOrNull()
                        CommandEffect.Print(
                            "Execution failed: ${error?.message ?: "unknown error"}",
                            isError = true,
                        )
                    }
                } catch (e: Exception) {
                    CommandEffect.Print("Execution error: ${e.message}", isError = true)
                }
            },
            onCancel = {
                CommandEffect.Print("Cancelled. No files were modified.")
            },
        )
    }
}
