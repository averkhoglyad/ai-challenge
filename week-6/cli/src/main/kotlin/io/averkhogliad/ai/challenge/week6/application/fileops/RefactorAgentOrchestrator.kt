package io.averkhogliad.ai.challenge.week6.application.fileops

import io.averkhogliad.ai.challenge.week6.application.AgentLoopService
import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileChange
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class RefactorAgentOrchestrator(
    private val agentLoopService: AgentLoopService,
    private val fileOpsPort: FileOpsPort,
    private val diffService: DiffService,
    private val projectContextProvider: ProjectContextProvider,
) {
    companion object {
        const val MAX_TOOL_CALLS_PER_ROUND = 20
        const val MAX_WRITE_OPS = 10
        const val MAX_FILE_CHANGES = 20
    }

    fun orchestrate(goal: String): Flow<RefactorProgress> = flow {
        emit(RefactorProgress.Planning)

        val systemPrompt = buildPlanningPrompt(goal)
        val planResponse = StringBuilder()
        try {
            agentLoopService.processQuery(
                query = goal,
                systemPromptOverride = systemPrompt,
                excludeExplicitTools = true,
                maxToolCalls = MAX_TOOL_CALLS_PER_ROUND,
            ).collect { chunk ->
                planResponse.append(chunk)
            }
        } catch (e: Exception) {
            emit(RefactorProgress.Error("Planning failed: ${e.message}"))
            return@flow
        }

        val changes = when (val result = parsePlan(planResponse.toString())) {
            is ParsePlanResult.Success -> result.changes
            is ParsePlanResult.Error -> {
                emit(RefactorProgress.Error(result.message))
                return@flow
            }
        }
        if (changes.isEmpty()) {
            emit(RefactorProgress.Error("Agent did not produce a valid plan."))
            return@flow
        }

        if (changes.size > MAX_FILE_CHANGES) {
            emit(RefactorProgress.Error("Plan exceeds $MAX_FILE_CHANGES files limit (got ${changes.size})"))
            return@flow
        }

        val writeOps = changes.count { it.newContent.isNotEmpty() }
        if (writeOps > MAX_WRITE_OPS) {
            emit(RefactorProgress.Error("Plan exceeds $MAX_WRITE_OPS write operations limit (got $writeOps)"))
            return@flow
        }

        emit(RefactorProgress.PlanReady(changes))

        // Track which files existed before (for safe rollback)
        val preexistingPaths = mutableSetOf<RelativePath>()
        val updatedChanges = changes.map { change ->
            val oldContent = when (val r = fileOpsPort.read(change.path)) {
                is DomainResult.Success -> {
                    preexistingPaths.add(change.path)
                    r.value.content
                }

                is DomainResult.Failure -> {
                    // Check if file exists at all (read may fail on binary files that DO exist)
                    when (val ex = fileOpsPort.exists(change.path)) {
                        is DomainResult.Success -> if (ex.value) preexistingPaths.add(change.path)
                        is DomainResult.Failure -> {}
                    }
                    null
                }
            }
            change.copy(oldContent = oldContent)
        }

        val diffs = diffService.buildDiffs(updatedChanges)
        emit(RefactorProgress.AwaitingConfirm(diffs, updatedChanges, preexistingPaths))
    }

    suspend fun executeChanges(
        changes: List<FileChange>,
        preexistingPaths: Set<RelativePath> = emptySet(),
    ): Flow<RefactorProgress> = flow {
        emit(RefactorProgress.Executing)

        val updatedFiles = mutableListOf<RelativePath>()
        var writeOps = 0

        for (change in changes) {
            if (writeOps >= MAX_WRITE_OPS) {
                val rollbackWarnings = rollback(updatedFiles, changes, preexistingPaths)
                emit(
                    RefactorProgress.Error(
                        "Write limit reached ($MAX_WRITE_OPS operations). Rolled back ${updatedFiles.size} file(s)." +
                                rollbackWarnings.asSuffix()
                    )
                )
                return@flow
            }

            when (val result = fileOpsPort.write(change.path, change.newContent)) {
                is DomainResult.Success -> {
                    updatedFiles.add(change.path)
                    writeOps++
                }

                is DomainResult.Failure -> {
                    val rollbackWarnings = rollback(updatedFiles, changes, preexistingPaths)
                    emit(
                        RefactorProgress.Error(
                            "Failed to write ${change.path}: ${result.error.message}. Rolled back ${updatedFiles.size} file(s)." +
                                    rollbackWarnings.asSuffix()
                        )
                    )
                    return@flow
                }
            }
        }

        emit(RefactorProgress.Completed(updatedFiles))
    }

    private suspend fun rollback(
        files: List<RelativePath>,
        changes: List<FileChange>,
        preexistingPaths: Set<RelativePath>,
    ): List<String> {
        val warnings = mutableListOf<String>()
        for (file in files) {
            val change = changes.find { it.path == file }
            if (change?.oldContent != null) {
                when (val r = fileOpsPort.write(file, change.oldContent)) {
                    is DomainResult.Failure -> warnings.add("Не удалось восстановить $file: ${r.error.message}")
                    is DomainResult.Success -> Unit
                }
            } else if (file in preexistingPaths) {
                // Read failed but file existed — best-effort: leave as-is, do not delete
                warnings.add("Не удалось восстановить $file: исходное содержимое недоступно")
            } else {
                when (val r = fileOpsPort.delete(file)) {
                    is DomainResult.Failure -> warnings.add("Не удалось удалить созданный файл $file: ${r.error.message}")
                    is DomainResult.Success -> Unit
                }
            }
        }
        return warnings
    }

    private suspend fun buildPlanningPrompt(goal: String): String {
        val ctx = when (val r = projectContextProvider.getContext()) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> null
        }

        return buildString {
            appendLine("You are a refactoring planner. Given a user goal, plan file changes.")
            appendLine("Use read_file, search_code, list_files to gather context about the project.")
            if (ctx != null) {
                appendLine("Project root: ${ctx.rootPath}")
            }
            appendLine()
            appendLine("IMPORTANT: Return your plan as a JSON array of file changes:")
            appendLine("[{\"path\": \"relative/path\", \"newContent\": \"full new content\"}]")
            appendLine()
            appendLine("Goal: $goal")
        }
    }

    private suspend fun parsePlan(response: String): ParsePlanResult {
        val ctx = when (val r = projectContextProvider.getContext()) {
            is DomainResult.Success -> r.value
            is DomainResult.Failure -> null
        } ?: return ParsePlanResult.Error("Не удалось получить контекст проекта для разбора плана.")

        val jsonStart = response.indexOf('[')
        val jsonEnd = response.lastIndexOf(']')
        if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
            return ParsePlanResult.Success(emptyList())
        }

        val jsonStr = response.substring(jsonStart, jsonEnd + 1)
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val array = json.decodeFromString<JsonArray>(jsonStr)
            ParsePlanResult.Success(
                array.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val path = (obj["path"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val newContent = (obj["newContent"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val relPath = RelativePath.from(path, ctx.rootPath).getOrNull() ?: return@mapNotNull null
                    FileChange(path = relPath, oldContent = null, newContent = newContent)
                }
            )
        } catch (_: SerializationException) {
            ParsePlanResult.Success(emptyList())
        } catch (e: Exception) {
            ParsePlanResult.Error("Не удалось разобрать план рефакторинга: ${e.message}")
        }
    }

    private fun List<String>.asSuffix(): String =
        takeIf { it.isNotEmpty() }?.joinToString(prefix = " Предупреждения отката: ", separator = "; ", postfix = ".")
            ?: ""

    private sealed interface ParsePlanResult {
        data class Success(val changes: List<FileChange>) : ParsePlanResult
        data class Error(val message: String) : ParsePlanResult
    }
}
