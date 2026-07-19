package io.averkhogliad.ai.challenge.week6.domain.indexer.usecase

import io.averkhogliad.ai.challenge.week6.domain.indexer.model.StalenessResult
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexMetadataStore
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import java.nio.file.Files
import java.nio.file.Path

class CheckIndexStalenessUseCase(
    private val gitPort: GitPort,
    private val metadataStore: IndexMetadataStore,
) {
    suspend fun execute(projectId: String, rootPath: Path): StalenessResult {
        val metadata = metadataStore.get(projectId) ?: return StalenessResult.NoIndex
        if (!Files.exists(rootPath.resolve(".git"))) return StalenessResult.NotApplicable

        val currentBranch = gitPort.getCurrentBranch(rootPath).getOrNull()
        val currentCommit = gitPort.getCurrentCommit(rootPath).getOrNull()

        // If both git calls failed, git may be unavailable — not a staleness issue
        if (currentBranch == null && currentCommit == null) return StalenessResult.NotApplicable

        return when {
            metadata.branch != null && metadata.branch != currentBranch ->
                StalenessResult.Stale("индекс создан на ветке ${metadata.branch}, сейчас: $currentBranch")

            metadata.commitHash != null && metadata.commitHash != currentCommit ->
                StalenessResult.Stale(
                    "индекс создан на коммите ${metadata.commitHash?.take(7)}, сейчас: ${
                        currentCommit?.take(
                            7
                        )
                    }"
                )

            else -> StalenessResult.Fresh
        }
    }
}
