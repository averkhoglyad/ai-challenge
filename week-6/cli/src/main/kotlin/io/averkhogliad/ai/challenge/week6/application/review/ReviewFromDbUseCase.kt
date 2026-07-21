package io.averkhogliad.ai.challenge.week6.application.review

import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.rag.RagService
import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewTrigger

open class ReviewFromDbUseCase(
    protected val ragService: RagService,
    protected val gitPort: GitPort,
    protected val llmClient: LlmClient,
    protected val saveReviewUseCase: SaveReviewUseCase,
    protected val projectContextProvider: ProjectContextProvider,
) {
    open suspend fun execute() {
        val ctx = getContext() ?: return
        println("🔍 Loading index from database...")
        ragService.loadIndexFromDb(ctx.projectId)
        executePostIndexSteps(ctx, ReviewTrigger.AUTO)
    }

    protected suspend fun getContext(): ProjectContext? {
        val ctx = projectContextProvider.getContext().getOrNull()
        if (ctx == null) {
            println("No active project. Use the REPL to open a project first.")
            return null
        }
        return ctx
    }

    protected suspend fun executePostIndexSteps(ctx: ProjectContext, trigger: ReviewTrigger) {
        val rootPath = ctx.rootPath

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
            trigger = trigger,
            commitHash = headHash,
            branch = branch,
        )

        flow.collect { chunk ->
            print(chunk)
        }

        println("\n✅ Review complete.")
    }
}
