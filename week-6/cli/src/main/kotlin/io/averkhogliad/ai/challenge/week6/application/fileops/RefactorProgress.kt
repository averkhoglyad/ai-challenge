package io.averkhogliad.ai.challenge.week6.application.fileops

import io.averkhogliad.ai.challenge.week6.domain.fileops.model.Diff
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileChange
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath

sealed interface RefactorProgress {
    data object Planning : RefactorProgress
    data class PlanReady(val changes: List<FileChange>) : RefactorProgress
    data class AwaitingConfirm(
        val diffs: List<Diff>,
        val changes: List<FileChange>,
        val preexistingPaths: Set<RelativePath> = emptySet(),
    ) : RefactorProgress

    data object Executing : RefactorProgress
    data class Completed(val updatedFiles: List<RelativePath>) : RefactorProgress
    data object Cancelled : RefactorProgress
    data class Error(val message: String) : RefactorProgress
}
