package io.averkhogliad.ai.challenge.week6

import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.rag.RagService
import io.averkhogliad.ai.challenge.week6.application.review.ReviewCodeUseCase
import io.averkhogliad.ai.challenge.week6.application.review.SaveReviewUseCase
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewTrigger
import java.nio.file.Path

class ReviewRunner(
    private val llmClient: LlmClient,
    private val ragService: RagService,
    private val gitPort: GitPort,
    private val saveReviewUseCase: SaveReviewUseCase,
    private val projectContextProvider: ProjectContextProvider,
) {
    suspend fun runReview(projectRoot: Path? = null) {
        val ctx = projectContextProvider.getContext().getOrNull()
        if (ctx == null) {
            println("No active project. Use the REPL to open a project first.")
            return
        }

        println("🔍 Loading index from database...")
        ragService.loadIndexFromDb(ctx.projectId)

        val rootPath = projectRoot ?: ctx.rootPath

        val headHash = gitPort.getLastCommitHash(rootPath).getOrNull()
        if (headHash == null) {
            println("❌ Cannot get HEAD commit hash.")
            return
        }

        val isMerge = gitPort.isMergeCommit(rootPath).getOrNull() ?: false
        if (isMerge) {
            println("⏭ Skipping merge commit.")
            return
        }

        val diff = gitPort.getDiffBetweenCommits(rootPath, "$headHash~1", headHash).getOrNull()
        if (diff == null) {
            println("❌ Cannot get diff.")
            return
        }

        val branch = gitPort.getCurrentBranch(rootPath).getOrNull()

        val reviewCodeUseCase = ReviewCodeUseCase(llmClient, ragService, saveReviewUseCase)
        val flow = reviewCodeUseCase.execute(
            projectId = ctx.projectId,
            diff = diff,
            trigger = ReviewTrigger.AUTO,
            commitHash = headHash,
            branch = branch,
        )

        flow.collect { chunk ->
            print(chunk)
        }

        println("\n✅ Review complete.")
    }
}
